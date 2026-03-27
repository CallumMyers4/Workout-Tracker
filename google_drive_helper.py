import io
import json
import mimetypes
import os
import pickle
import ssl
import sys
import time
import urllib.parse
import urllib.error
import urllib.request

SCOPES = ["https://www.googleapis.com/auth/drive.file"]
ANDROID_AUTH_REQUEST_CODE = 48271


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


def get_android_auth_modules():
    try:
        from jnius import PythonJavaClass, autoclass, java_method
        from android.activity import bind, unbind
    except Exception as exc:
        raise RuntimeError(
            "Android Google authorization is unavailable. "
            "Ensure the APK includes pyjnius and Google Play Services Auth."
        ) from exc

    return PythonJavaClass, autoclass, java_method, bind, unbind


class GoogleDriveHelper:
    def __init__(
        self,
        credentials_name="credentials.json",
        token_name="token.pickle",
        auto_login=True,
    ):
        self.credentials_path = resource_path(credentials_name)

        appdata = get_app_data_dir()
        token_dir = os.path.join(appdata, "WorkoutTracker")
        os.makedirs(token_dir, exist_ok=True)

        self.token_path = os.path.join(token_dir, token_name)
        self.android_token_path = os.path.join(token_dir, "token_android.json")
        self.creds = None
        self.service = None
        self._android_auth_success = None
        self._android_auth_failure = None
        self._android_activity_bound = False
        self._android_success_listener = None
        self._android_failure_listener = None
        self._android_authorization_task = None
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
            try:
                get_android_auth_modules()
            except RuntimeError as exc:
                return False, str(exc)
            return True, "Android Drive sign-in is available through native Google authorization."

        try:
            get_google_client_modules()
        except RuntimeError as exc:
            return False, str(exc)

        if not os.path.exists(resource_path("credentials.json")):
            return False, "Desktop Google Drive sign-in needs a local credentials.json file."

        return True, "Google Drive sync is available."

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
            if is_android_environment() and exc.code == 401:
                self.clear_android_token()
            raise RuntimeError(self._format_http_error(exc)) from exc

    def _http_get_json(self, url, access_token):
        request = urllib.request.Request(url, headers=self._android_json_headers(access_token), method="GET")
        try:
            with urllib.request.urlopen(request, timeout=30, context=self._ssl_context()) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if is_android_environment() and exc.code == 401:
                self.clear_android_token()
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
            if is_android_environment() and exc.code == 401:
                self.clear_android_token()
            raise RuntimeError(self._format_http_error(exc)) from exc

    def _http_download_file(self, url, access_token, destination_path):
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {access_token}"}, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=60, context=self._ssl_context()) as response, open(destination_path, "wb") as target_file:
                target_file.write(response.read())
        except urllib.error.HTTPError as exc:
            if is_android_environment() and exc.code == 401:
                self.clear_android_token()
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

    def clear_android_token(self):
        if os.path.exists(self.android_token_path):
            os.remove(self.android_token_path)

    def has_saved_android_token(self):
        token_data = self._load_android_token()
        return bool(token_data and token_data.get("access_token"))

    def _get_android_authorization_client(self):
        _python_java_class, autoclass, _java_method, _bind, _unbind = get_android_auth_modules()
        identity = autoclass("com.google.android.gms.auth.api.identity.Identity")
        python_activity = autoclass("org.kivy.android.PythonActivity")
        activity = python_activity.mActivity
        return identity.getAuthorizationClient(activity), activity, autoclass

    def _build_android_authorization_request(self):
        _python_java_class, autoclass, _java_method, _bind, _unbind = get_android_auth_modules()
        authorization_request = autoclass("com.google.android.gms.auth.api.identity.AuthorizationRequest")
        scope_class = autoclass("com.google.android.gms.common.api.Scope")
        array_list = autoclass("java.util.ArrayList")()
        for scope in SCOPES:
            array_list.add(scope_class(scope))
        return authorization_request.builder().setRequestedScopes(array_list).build()

    def _format_java_exception(self, exc):
        message = str(exc)
        for candidate in ("getStatusMessage", "getMessage", "toString"):
            try:
                value = getattr(exc, candidate)()
                if value:
                    return str(value)
            except Exception:
                continue
        return message or exc.__class__.__name__

    def _bind_android_activity_result(self):
        _python_java_class, _autoclass, _java_method, bind, _unbind = get_android_auth_modules()
        if not self._android_activity_bound:
            bind(on_activity_result=self._on_android_activity_result)
            self._android_activity_bound = True

    def _unbind_android_activity_result(self):
        _python_java_class, _autoclass, _java_method, _bind, unbind = get_android_auth_modules()
        if self._android_activity_bound:
            unbind(on_activity_result=self._on_android_activity_result)
            self._android_activity_bound = False

    def _finish_android_authorization_success(self, access_token):
        self._save_android_token(
            {
                "access_token": access_token,
                "saved_at": int(time.time()),
            }
        )
        callback = self._android_auth_success
        self._android_auth_success = None
        self._android_auth_failure = None
        self._android_success_listener = None
        self._android_failure_listener = None
        self._android_authorization_task = None
        self._unbind_android_activity_result()
        if callback:
            callback(access_token)

    def _finish_android_authorization_failure(self, message):
        callback = self._android_auth_failure
        self._android_auth_success = None
        self._android_auth_failure = None
        self._android_success_listener = None
        self._android_failure_listener = None
        self._android_authorization_task = None
        self._unbind_android_activity_result()
        if callback:
            callback(message)

    def _extract_android_access_token(self, authorization_result):
        access_token = authorization_result.getAccessToken()
        if not access_token:
            raise RuntimeError("Google did not return an access token.")
        return access_token

    def _handle_android_authorization_result(self, authorization_result):
        try:
            if authorization_result.hasResolution():
                pending_intent = authorization_result.getPendingIntent()
                _client, activity, _autoclass = self._get_android_authorization_client()
                activity.startIntentSenderForResult(
                    pending_intent.getIntentSender(),
                    ANDROID_AUTH_REQUEST_CODE,
                    None,
                    0,
                    0,
                    0,
                )
                return

            access_token = self._extract_android_access_token(authorization_result)
            self._finish_android_authorization_success(access_token)
        except Exception as exc:
            self._finish_android_authorization_failure(self._format_java_exception(exc))

    def _on_android_activity_result(self, request_code, result_code, intent):
        if request_code != ANDROID_AUTH_REQUEST_CODE:
            return
        try:
            authorization_client, _activity, _autoclass = self._get_android_authorization_client()
            authorization_result = authorization_client.getAuthorizationResultFromIntent(intent)
            access_token = self._extract_android_access_token(authorization_result)
            self._finish_android_authorization_success(access_token)
        except Exception as exc:
            self._finish_android_authorization_failure(self._format_java_exception(exc))

    def _get_android_access_token(self):
        token_data = self._load_android_token()
        if not token_data:
            raise RuntimeError("No Android Google Drive token found. Start sign-in from Settings first.")
        return token_data["access_token"]

    def start_native_android_authorization(self, on_success, on_failure):
        python_java_class, _autoclass, java_method, _bind, _unbind = get_android_auth_modules()
        authorization_client, _activity, _autoclass = self._get_android_authorization_client()
        request = self._build_android_authorization_request()
        self._android_auth_success = on_success
        self._android_auth_failure = on_failure
        self._bind_android_activity_result()

        class SuccessListener(python_java_class):
            __javainterfaces__ = ["com/google/android/gms/tasks/OnSuccessListener"]
            __javacontext__ = "app"

            def __init__(self, callback):
                super().__init__()
                self.callback = callback

            @java_method("(Ljava/lang/Object;)V")
            def onSuccess(self, result):
                self.callback(result)

        class FailureListener(python_java_class):
            __javainterfaces__ = ["com/google/android/gms/tasks/OnFailureListener"]
            __javacontext__ = "app"

            def __init__(self, callback):
                super().__init__()
                self.callback = callback

            @java_method("(Ljava/lang/Exception;)V")
            def onFailure(self, exc):
                self.callback(exc)

        def failure_callback(exc):
            self._finish_android_authorization_failure(self._format_java_exception(exc))

        task = authorization_client.authorize(request)
        self._android_success_listener = SuccessListener(self._handle_android_authorization_result)
        self._android_failure_listener = FailureListener(failure_callback)
        self._android_authorization_task = task
        task.addOnSuccessListener(self._android_success_listener)
        task.addOnFailureListener(self._android_failure_listener)

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
            create_metadata = {"name": file_name, "parents": [folder_id]}
            update_metadata = {"name": file_name}

            if items:
                updated = self._http_patch_or_post_multipart(
                    f"https://www.googleapis.com/upload/drive/v3/files/{items[0]['id']}?uploadType=multipart&fields=id",
                    update_metadata,
                    file_path,
                    access_token,
                    "PATCH",
                )
                return updated.get("id")

            created = self._http_patch_or_post_multipart(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                create_metadata,
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
            query_params = urllib.parse.urlencode(
                {
                    "q": f"'{folder_id}' in parents and trashed=false",
                    "fields": "files(id,name,modifiedTime)",
                    "orderBy": "modifiedTime desc",
                }
            )
            results = self._http_get_json(
                f"https://www.googleapis.com/drive/v3/files?{query_params}",
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
            if not downloaded:
                raise FileNotFoundError(f"No matching files found in folder '{folder_name}'")
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

        if not downloaded:
            raise FileNotFoundError(f"No matching files found in folder '{folder_name}'")

        return downloaded
