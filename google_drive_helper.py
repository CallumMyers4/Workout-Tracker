import io
import json
import mimetypes
import os
import pickle
import secrets
import ssl
import threading
import sys
import time
import urllib.parse
import urllib.error
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer

SCOPES = ["https://www.googleapis.com/auth/drive.file"]
PUBLIC_OAUTH_CONFIG_NAME = "drive_oauth_config.json"
PRIVATE_OAUTH_SECRET_NAME = "drive_oauth_secret.json"


def is_android_environment():
    return "ANDROID_ARGUMENT" in os.environ or "ANDROID_PRIVATE" in os.environ


def _get_running_app():
    try:
        from kivy.app import App

        return App.get_running_app()
    except Exception:
        return None


def get_app_data_dir():
    app = _get_running_app()
    if app and getattr(app, "user_data_dir", None):
        return app.user_data_dir

    if is_android_environment():
        private_dir = os.environ.get("ANDROID_PRIVATE")
        if private_dir:
            return private_dir
        try:
            from android.storage import app_storage_path

            return app_storage_path()
        except Exception:
            pass

    return os.getenv("APPDATA") or os.path.expanduser("~")


def _resource_candidates(filename):
    candidates = []

    if hasattr(sys, "frozen") and getattr(sys, "_MEIPASS", None):
        candidates.append(os.path.join(sys._MEIPASS, filename))

    module_dir = os.path.dirname(os.path.abspath(__file__))
    candidates.append(os.path.join(module_dir, filename))
    candidates.append(os.path.join(os.getcwd(), filename))

    app = _get_running_app()
    if app and getattr(app, "user_data_dir", None):
        candidates.append(os.path.join(app.user_data_dir, filename))

    android_private = os.environ.get("ANDROID_PRIVATE")
    if android_private:
        candidates.append(os.path.join(android_private, filename))

    if is_android_environment():
        try:
            from android.storage import app_storage_path

            candidates.append(os.path.join(app_storage_path(), filename))
        except Exception:
            pass

    seen = set()
    ordered = []
    for candidate in candidates:
        if candidate and candidate not in seen:
            ordered.append(candidate)
            seen.add(candidate)
    return ordered


def resource_path(filename):
    """
    Resolve a bundled/support file from the most likely runtime locations.
    """
    for candidate in _resource_candidates(filename):
        if os.path.exists(candidate):
            return candidate
    return _resource_candidates(filename)[0]


def get_google_client_modules():
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
    def __init__(
        self,
        credentials_name="credentials.json",
        public_config_name=PUBLIC_OAUTH_CONFIG_NAME,
        token_name="token.pickle",
        auto_login=True,
    ):
        self.credentials_path = resource_path(credentials_name)
        self.public_config_path = resource_path(public_config_name)
        self.public_secret_path = resource_path(PRIVATE_OAUTH_SECRET_NAME)

        appdata = get_app_data_dir()
        token_dir = os.path.join(appdata, "WorkoutTracker")
        os.makedirs(token_dir, exist_ok=True)

        self.token_path = os.path.join(token_dir, token_name)
        self.android_token_path = os.path.join(token_dir, "token_android.json")
        self.creds = None
        self.service = None
        self.device_flow = None
        self.android_auth_flow = None
        self._configure_tls_certificates()

        if auto_login:
            self.login()

    def _configure_tls_certificates(self):
        try:
            import certifi

            cert_path = certifi.where()
        except Exception:
            cert_path = None

        if cert_path:
            os.environ.setdefault("SSL_CERT_FILE", cert_path)
            os.environ.setdefault("REQUESTS_CA_BUNDLE", cert_path)

    def _ssl_context(self):
        try:
            import certifi

            return ssl.create_default_context(cafile=certifi.where())
        except Exception:
            return ssl.create_default_context()

    @staticmethod
    def environment_status():
        if is_android_environment():
            if not os.path.exists(resource_path(PUBLIC_OAUTH_CONFIG_NAME)):
                return False, "Android Drive config file is missing."
            return (
                True,
                "Android Drive sign-in is available in beta using an in-app authorization flow.",
            )

        try:
            get_google_client_modules()
        except RuntimeError as exc:
            return False, str(exc)

        if not os.path.exists(resource_path("credentials.json")):
            return False, "Desktop Google Drive sign-in needs a local credentials.json file."

        return True, "Google Drive sync is available."

    def _read_credentials_info(self, allow_private=True):
        if allow_private and os.path.exists(self.credentials_path):
            with open(self.credentials_path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
            return data.get("installed") or data.get("web") or {}

        if os.path.exists(self.public_config_path):
            with open(self.public_config_path, "r", encoding="utf-8") as handle:
                config = json.load(handle)
            if os.path.exists(self.public_secret_path):
                with open(self.public_secret_path, "r", encoding="utf-8") as handle:
                    secret_data = json.load(handle)
                if isinstance(secret_data, dict):
                    config.update({key: value for key, value in secret_data.items() if value not in (None, "")})
            return config

        raise RuntimeError(
            "No Google OAuth configuration found. "
            "Expected credentials.json for desktop or drive_oauth_config.json for Android/public builds."
        )

    def _android_token_headers(self):
        return {"Content-Type": "application/x-www-form-urlencoded"}

    def _android_json_headers(self, access_token):
        return {
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json",
        }

    def _format_http_error(self, exc):
        body_text = ""
        try:
            body_text = exc.read().decode("utf-8", errors="replace")
        except Exception:
            body_text = ""

        if body_text:
            try:
                payload = json.loads(body_text)
                error_code = payload.get("error")
                error_description = payload.get("error_description")
                if error_code and error_description:
                    return f"{exc.code} {error_code}: {error_description}"
                if error_code:
                    return f"{exc.code} {error_code}"
                if "error" in payload:
                    return f"{exc.code}: {payload['error']}"
            except Exception:
                pass
            return f"{exc.code}: {body_text}"

        return f"{exc.code}: {exc.reason}"

    def _http_post_form(self, url, payload):
        data = urllib.parse.urlencode(payload).encode("utf-8")
        request = urllib.request.Request(url, data=data, headers=self._android_token_headers(), method="POST")
        try:
            with urllib.request.urlopen(request, timeout=30, context=self._ssl_context()) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise RuntimeError(self._format_http_error(exc)) from exc

    def _http_get_json(self, url, access_token):
        request = urllib.request.Request(url, headers=self._android_json_headers(access_token), method="GET")
        try:
            with urllib.request.urlopen(request, timeout=30, context=self._ssl_context()) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise RuntimeError(self._format_http_error(exc)) from exc

    def _http_patch_or_post_multipart(self, url, metadata, file_path, access_token, method):
        boundary = "workouttrackerboundary"
        mime_type = mimetypes.guess_type(file_path)[0] or "application/octet-stream"

        with open(file_path, "rb") as source_file:
            file_bytes = source_file.read()

        body = []
        body.append(f"--{boundary}\r\n".encode("utf-8"))
        body.append(b"Content-Type: application/json; charset=UTF-8\r\n\r\n")
        body.append(json.dumps(metadata).encode("utf-8"))
        body.append(b"\r\n")
        body.append(f"--{boundary}\r\n".encode("utf-8"))
        body.append(f"Content-Type: {mime_type}\r\n\r\n".encode("utf-8"))
        body.append(file_bytes)
        body.append(b"\r\n")
        body.append(f"--{boundary}--\r\n".encode("utf-8"))

        request = urllib.request.Request(
            url,
            data=b"".join(body),
            method=method,
            headers={
                "Authorization": f"Bearer {access_token}",
                "Content-Type": f"multipart/related; boundary={boundary}",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=60, context=self._ssl_context()) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise RuntimeError(self._format_http_error(exc)) from exc

    def _http_download_file(self, url, access_token, destination_path):
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {access_token}"}, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=60, context=self._ssl_context()) as response, open(destination_path, "wb") as target_file:
                target_file.write(response.read())
        except urllib.error.HTTPError as exc:
            raise RuntimeError(self._format_http_error(exc)) from exc
        return destination_path

    def _load_android_token(self):
        if not os.path.exists(self.android_token_path):
            return None
        with open(self.android_token_path, "r", encoding="utf-8") as handle:
            return json.load(handle)

    def _save_android_token(self, token_data):
        with open(self.android_token_path, "w", encoding="utf-8") as handle:
            json.dump(token_data, handle)

    def _base64url_sha256(self, value):
        import base64
        import hashlib

        digest = hashlib.sha256(value.encode("utf-8")).digest()
        return base64.urlsafe_b64encode(digest).decode("utf-8").rstrip("=")

    def _start_loopback_server(self):
        holder = {"code": None, "error": None}

        class CallbackHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                parsed = urllib.parse.urlparse(self.path)
                params = urllib.parse.parse_qs(parsed.query)
                holder["code"] = params.get("code", [None])[0]
                holder["error"] = params.get("error", [None])[0]
                body = (
                    "<html><body style='font-family:sans-serif;padding:24px;'>"
                    "<h2>Workout Tracker</h2><p>Sign-in received. You can return to the app now.</p>"
                    "</body></html>"
                ).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, _format, *_args):
                return

        server = HTTPServer(("127.0.0.1", 0), CallbackHandler)
        port = server.server_address[1]

        def run_server():
            try:
                server.handle_request()
            finally:
                server.server_close()

        thread = threading.Thread(target=run_server, daemon=True)
        thread.start()
        return server, thread, port, holder

    def _refresh_android_token(self, token_data):
        credentials = self._read_credentials_info(allow_private=False)
        refresh_token = token_data.get("refresh_token")
        if not refresh_token:
            return token_data

        refreshed = self._http_post_form(
            credentials["token_uri"],
            {
                "client_id": credentials["client_id"],
                **({"client_secret": credentials["client_secret"]} if credentials.get("client_secret") else {}),
                "refresh_token": refresh_token,
                "grant_type": "refresh_token",
            },
        )

        token_data["access_token"] = refreshed["access_token"]
        token_data["expires_at"] = int(time.time()) + int(refreshed.get("expires_in", 3600))
        self._save_android_token(token_data)
        return token_data

    def _get_android_access_token(self):
        token_data = self._load_android_token()
        if not token_data:
            raise RuntimeError("No Android Google Drive token found. Start sign-in from Settings first.")

        if token_data.get("expires_at", 0) <= int(time.time()) + 60:
            token_data = self._refresh_android_token(token_data)

        return token_data["access_token"]

    def start_android_login(self):
        credentials = self._read_credentials_info(allow_private=False)
        server, thread, port, holder = self._start_loopback_server()
        code_verifier = secrets.token_urlsafe(64)
        code_challenge = self._base64url_sha256(code_verifier)
        redirect_uri = f"http://127.0.0.1:{port}/callback"
        state = secrets.token_urlsafe(24)

        auth_url = (
            "https://accounts.google.com/o/oauth2/v2/auth?"
            + urllib.parse.urlencode(
                {
                    "client_id": credentials["client_id"],
                    "redirect_uri": redirect_uri,
                    "response_type": "code",
                    "scope": " ".join(SCOPES),
                    "access_type": "offline",
                    "prompt": "consent",
                    "code_challenge": code_challenge,
                    "code_challenge_method": "S256",
                    "state": state,
                }
            )
        )
        self.android_auth_flow = {
            "redirect_uri": redirect_uri,
            "code_verifier": code_verifier,
            "state": state,
            "holder": holder,
            "thread": thread,
            "server": server,
            "auth_url": auth_url,
        }
        return {"auth_url": auth_url}

    def finish_android_login(self):
        if not self.android_auth_flow:
            raise RuntimeError("No pending Android sign-in. Start sign-in first.")

        flow = self.android_auth_flow
        flow["thread"].join(timeout=1.0)
        auth_code = flow["holder"].get("code")
        auth_error = flow["holder"].get("error")
        if auth_error:
            self.android_auth_flow = None
            raise RuntimeError(auth_error.replace("_", " ").title())
        if not auth_code:
            raise RuntimeError("Authorization code not received yet. Finish approval in the browser and try again.")

        credentials = self._read_credentials_info(allow_private=False)
        token_data = self._http_post_form(
            credentials["token_uri"],
            {
                "client_id": credentials["client_id"],
                "code": auth_code,
                "code_verifier": flow["code_verifier"],
                "grant_type": "authorization_code",
                "redirect_uri": flow["redirect_uri"],
            },
        )

        persisted = {
            "access_token": token_data["access_token"],
            "refresh_token": token_data.get("refresh_token"),
            "expires_at": int(time.time()) + int(token_data.get("expires_in", 3600)),
        }
        self._save_android_token(persisted)
        self.android_auth_flow = None
        return persisted

    def login(self):
        available, reason = self.environment_status()
        if not available:
            raise RuntimeError(reason)

        if is_android_environment():
            self._get_android_access_token()
            return

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
        if self.service is None:
            self.login()

    def upload_file(self, file_path, file_name=None, mime_type="application/octet-stream"):
        self._ensure_service()
        _build, media_upload_cls, _media_download, _installed_app_flow, _request_cls = get_google_client_modules()

        if not file_name:
            file_name = os.path.basename(file_path)

        file_metadata = {"name": file_name}
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

        created = self.service.files().create(body=file_metadata, media_body=media, fields="id").execute()
        return created.get("id")

    def download_latest_backup(self, file_name="workouts_backup.db", destination_path="workouts.db"):
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

        request = self.service.files().get_media(fileId=items[0]["id"])
        with io.FileIO(destination_path, "wb") as file_handle:
            downloader = media_download_cls(file_handle, request)
            done = False
            while not done:
                _status, done = downloader.next_chunk()

        return destination_path

    def get_or_create_folder(self, folder_name="Workout Tracker Backups"):
        if is_android_environment():
            access_token = self._get_android_access_token()
            encoded_query = urllib.parse.quote(
                f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            )
            results = self._http_get_json(
                f"https://www.googleapis.com/drive/v3/files?q={encoded_query}&fields=files(id,name)",
                access_token,
            )
            items = results.get("files", [])
            if items:
                return items[0]["id"]

            metadata = json.dumps({"name": folder_name, "mimeType": "application/vnd.google-apps.folder"}).encode("utf-8")
            request = urllib.request.Request(
                "https://www.googleapis.com/drive/v3/files?fields=id",
                data=metadata,
                headers={
                    "Authorization": f"Bearer {access_token}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(request, timeout=30, context=self._ssl_context()) as response:
                    return json.loads(response.read().decode("utf-8"))["id"]
            except urllib.error.HTTPError as exc:
                raise RuntimeError(self._format_http_error(exc)) from exc

        self._ensure_service()

        results = self.service.files().list(
            q=f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder'",
            spaces="drive",
            fields="files(id, name)",
        ).execute()

        items = results.get("files", [])
        if items:
            return items[0]["id"]

        metadata = {"name": folder_name, "mimeType": "application/vnd.google-apps.folder"}
        folder = self.service.files().create(body=metadata, fields="id").execute()
        return folder.get("id")

    def upload_to_folder(self, file_path, folder_name="Workout Tracker Backups", mime_type="application/octet-stream"):
        if is_android_environment():
            access_token = self._get_android_access_token()
            folder_id = self.get_or_create_folder(folder_name)
            file_name = os.path.basename(file_path)
            encoded_query = urllib.parse.quote(
                f"name='{file_name}' and '{folder_id}' in parents and trashed=false"
            )
            results = self._http_get_json(
                f"https://www.googleapis.com/drive/v3/files?q={encoded_query}&fields=files(id,name)",
                access_token,
            )
            items = results.get("files", [])
            metadata = {"name": file_name, "parents": [folder_id]}

            if items:
                updated = self._http_patch_or_post_multipart(
                    f"https://www.googleapis.com/upload/drive/v3/files/{items[0]['id']}?uploadType=multipart&fields=id",
                    metadata,
                    file_path,
                    access_token,
                    "PATCH",
                )
                return updated.get("id")

            created = self._http_patch_or_post_multipart(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                metadata,
                file_path,
                access_token,
                "POST",
            )
            return created.get("id")

        self._ensure_service()
        _build, media_upload_cls, _media_download, _installed_app_flow, _request_cls = get_google_client_modules()

        folder_id = self.get_or_create_folder(folder_name)
        file_name = os.path.basename(file_path)
        query = f"name='{file_name}' and '{folder_id}' in parents and trashed=false"
        results = self.service.files().list(q=query, spaces="drive", fields="files(id, name)").execute()

        items = results.get("files", [])
        media = media_upload_cls(file_path, mimetype=mime_type)

        if items:
            updated = self.service.files().update(fileId=items[0]["id"], media_body=media).execute()
            return updated.get("id")

        metadata = {"name": file_name, "parents": [folder_id]}
        created = self.service.files().create(body=metadata, media_body=media, fields="id").execute()
        return created.get("id")

    def download_from_folder(self, folder_name="Workout Tracker Backups", local_dir=".", files=None):
        if is_android_environment():
            access_token = self._get_android_access_token()
            folder_id = self.get_or_create_folder(folder_name)
            encoded_query = urllib.parse.quote(f"'{folder_id}' in parents and trashed=false")
            results = self._http_get_json(
                f"https://www.googleapis.com/drive/v3/files?q={encoded_query}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc",
                access_token,
            )
            items = results.get("files", [])
            if not items:
                raise FileNotFoundError(f"No files found in folder '{folder_name}'")

            downloaded = []
            for item in items:
                if files is None or item["name"] in files:
                    local_path = os.path.join(local_dir, item["name"])
                    self._http_download_file(
                        f"https://www.googleapis.com/drive/v3/files/{item['id']}?alt=media",
                        access_token,
                        local_path,
                    )
                    downloaded.append(local_path)
            return downloaded

        self._ensure_service()
        _build, _media_upload, media_download_cls, _installed_app_flow, _request_cls = get_google_client_modules()

        folder_id = self.get_or_create_folder(folder_name)
        results = self.service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc",
        ).execute()

        items = results.get("files", [])
        if not items:
            raise FileNotFoundError(f"No files found in folder '{folder_name}'")

        downloaded = []
        for item in items:
            if files is None or item["name"] in files:
                local_path = os.path.join(local_dir, item["name"])
                request = self.service.files().get_media(fileId=item["id"])
                with io.FileIO(local_path, "wb") as file_handle:
                    downloader = media_download_cls(file_handle, request)
                    done = False
                    while not done:
                        _status, done = downloader.next_chunk()
                downloaded.append(local_path)

        return downloaded
