"""Desktop Google Drive backup support used by the PyQt application."""

import io
import os
import pickle
import sys


SCOPES = ["https://www.googleapis.com/auth/drive.file"]


def get_app_data_dir():
    """Return the platform-specific directory used for the desktop OAuth token."""
    return os.getenv("APPDATA") or os.path.expanduser("~")


def _resource_candidates(filename):
    """Return possible locations for a bundled desktop support file."""
    candidates = []
    if hasattr(sys, "frozen") and getattr(sys, "_MEIPASS", None):
        candidates.append(os.path.join(sys._MEIPASS, filename))

    module_dir = os.path.dirname(os.path.abspath(__file__))
    candidates.append(os.path.join(module_dir, filename))
    candidates.append(os.path.join(os.getcwd(), filename))

    seen = set()
    return [candidate for candidate in candidates if not (candidate in seen or seen.add(candidate))]


def resource_path(filename):
    """Resolve a support file from the desktop source or packaged application."""
    for candidate in _resource_candidates(filename):
        if os.path.exists(candidate):
            return candidate
    return _resource_candidates(filename)[0]


def get_google_client_modules():
    """Import optional Google libraries only when Drive features are used."""
    try:
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload, MediaIoBaseDownload
        from google_auth_oauthlib.flow import InstalledAppFlow
        from google.auth.transport.requests import Request
    except Exception as exc:
        raise RuntimeError(
            "Google Drive libraries are not installed. "
            "Install google-api-python-client, google-auth-oauthlib, and google-auth-httplib2."
        ) from exc

    return build, MediaFileUpload, MediaIoBaseDownload, InstalledAppFlow, Request


class GoogleDriveHelper:
    """Authenticate the desktop user and upload or download workout database files."""

    def __init__(
        self,
        credentials_name="config/credentials.json",
        token_name="token.pickle",
        auto_login=True,
    ):
        self.credentials_path = resource_path(credentials_name)
        token_dir = os.path.join(get_app_data_dir(), "WorkoutTracker")
        os.makedirs(token_dir, exist_ok=True)
        self.token_path = os.path.join(token_dir, token_name)
        self.creds = None
        self.service = None
        self._configure_tls_certificates()

        if auto_login:
            self.login()

    def _configure_tls_certificates(self):
        """Point Google and Requests libraries at certifi when it is available."""
        try:
            import certifi

            cert_path = certifi.where()
        except Exception:
            cert_path = None

        if cert_path:
            os.environ.setdefault("SSL_CERT_FILE", cert_path)
            os.environ.setdefault("REQUESTS_CA_BUNDLE", cert_path)

    @staticmethod
    def environment_status():
        """Return whether desktop credentials and Google libraries are available."""
        try:
            get_google_client_modules()
        except RuntimeError as exc:
            return False, str(exc)

        if not os.path.exists(resource_path("config/credentials.json")):
            return False, "Desktop Google Drive sign-in needs a local config/credentials.json file."

        return True, "Google Drive sync is available."

    def login(self):
        """Load, refresh, or request desktop OAuth credentials and create the Drive service."""
        available, reason = self.environment_status()
        if not available:
            raise RuntimeError(reason)

        build, _media_upload, _media_download, installed_app_flow, request_cls = get_google_client_modules()
        if os.path.exists(self.token_path):
            with open(self.token_path, "rb") as token:
                self.creds = pickle.load(token)

        if not self.creds or not self.creds.valid:
            if self.creds and self.creds.expired and self.creds.refresh_token:
                self.creds.refresh(request_cls())
            else:
                flow = installed_app_flow.from_client_secrets_file(self.credentials_path, SCOPES)
                self.creds = flow.run_local_server(port=0)

            with open(self.token_path, "wb") as token:
                pickle.dump(self.creds, token)

        self.service = build("drive", "v3", credentials=self.creds)

    def _ensure_service(self):
        """Log in before the first Drive operation when automatic login was disabled."""
        if self.service is None:
            self.login()

    def upload_file(self, file_path, file_name=None, mime_type="application/octet-stream"):
        """Create or replace a named file in the user's Drive."""
        self._ensure_service()
        _build, media_upload_cls, _media_download, _installed_app_flow, _request_cls = get_google_client_modules()
        file_name = file_name or os.path.basename(file_path)
        media = media_upload_cls(file_path, mimetype=mime_type)
        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc",
        ).execute()

        items = results.get("files", [])
        if items:
            updated = self.service.files().update(fileId=items[0]["id"], media_body=media).execute()
            return updated.get("id")

        created = self.service.files().create(
            body={"name": file_name},
            media_body=media,
            fields="id",
        ).execute()
        return created.get("id")

    def download_latest_backup(self, file_name="workouts_backup.db", destination_path="workouts.db"):
        """Download the most recently modified Drive file with the requested name."""
        self._ensure_service()
        _build, _media_upload, media_download_cls, _installed_app_flow, _request_cls = get_google_client_modules()
        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc",
        ).execute()
        items = results.get("files", [])
        if not items:
            raise FileNotFoundError(f"No backup named {file_name} found.")

        self._download_file(items[0]["id"], destination_path, media_download_cls)
        return destination_path

    def get_or_create_folder(self, folder_name="Workout Tracker Backups"):
        """Return the Drive ID of the backup folder, creating it when required."""
        self._ensure_service()
        results = self.service.files().list(
            q=f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder'",
            spaces="drive",
            fields="files(id, name)",
        ).execute()
        items = results.get("files", [])
        if items:
            return items[0]["id"]

        folder = self.service.files().create(
            body={"name": folder_name, "mimeType": "application/vnd.google-apps.folder"},
            fields="id",
        ).execute()
        return folder.get("id")

    def upload_to_folder(self, file_path, folder_name="Workout Tracker Backups", mime_type="application/octet-stream"):
        """Create or replace a file inside the desktop app's Drive backup folder."""
        self._ensure_service()
        _build, media_upload_cls, _media_download, _installed_app_flow, _request_cls = get_google_client_modules()
        folder_id = self.get_or_create_folder(folder_name)
        file_name = os.path.basename(file_path)
        query = f"name='{file_name}' and '{folder_id}' in parents and trashed=false"
        items = self.service.files().list(
            q=query,
            spaces="drive",
            fields="files(id, name)",
        ).execute().get("files", [])
        media = media_upload_cls(file_path, mimetype=mime_type)

        if items:
            updated = self.service.files().update(fileId=items[0]["id"], media_body=media).execute()
            return updated.get("id")

        created = self.service.files().create(
            body={"name": file_name, "parents": [folder_id]},
            media_body=media,
            fields="id",
        ).execute()
        return created.get("id")

    def download_from_folder(self, folder_name="Workout Tracker Backups", local_dir=".", files=None):
        """Download selected files from the backup folder in newest-first order."""
        self._ensure_service()
        _build, _media_upload, media_download_cls, _installed_app_flow, _request_cls = get_google_client_modules()
        folder_id = self.get_or_create_folder(folder_name)
        items = self.service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc",
        ).execute().get("files", [])
        if not items:
            raise FileNotFoundError(f"No files found in folder '{folder_name}'")

        downloaded = []
        for item in items:
            if files is None or item["name"] in files:
                local_path = os.path.join(local_dir, item["name"])
                self._download_file(item["id"], local_path, media_download_cls)
                downloaded.append(local_path)

        if not downloaded:
            raise FileNotFoundError(f"No matching files found in folder '{folder_name}'")
        return downloaded

    def _download_file(self, file_id, destination_path, media_download_cls):
        """Stream one Drive file into a local destination."""
        request = self.service.files().get_media(fileId=file_id)
        with io.FileIO(destination_path, "wb") as file_handle:
            downloader = media_download_cls(file_handle, request)
            done = False
            while not done:
                _status, done = downloader.next_chunk()
