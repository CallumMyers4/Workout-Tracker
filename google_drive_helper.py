# google_drive_helper.py

import io
import os
import sys
import pickle

from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload, MediaIoBaseDownload
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

SCOPES = ["https://www.googleapis.com/auth/drive.file"]


# -------------------------------
# PyInstaller-safe resource loader
# -------------------------------
def resource_path(filename):
    """
    When running under PyInstaller, data files are stored in sys._MEIPASS.
    When running as normal Python, use the directory this file is in.
    """
    if hasattr(sys, "frozen"):
        return os.path.join(sys._MEIPASS, filename)
    return os.path.join(os.path.dirname(__file__), filename)


class GoogleDriveHelper:
    def __init__(self, credentials_name="credentials.json", token_name="token.pickle"):

        # 1. Credentials.json (bundled inside the .exe)
        self.credentials_path = resource_path(credentials_name)

        # 2. token.pickle (must be stored OUTSIDE the .exe!)
        appdata = os.getenv("APPDATA") or os.path.expanduser("~")
        token_dir = os.path.join(appdata, "WorkoutTracker")
        os.makedirs(token_dir, exist_ok=True)

        self.token_path = os.path.join(token_dir, token_name)

        self.creds = None
        self.service = None
        self.login()

    # -----------------------------------------------------
    # LOGIN AND AUTHENTICATION
    # -----------------------------------------------------
    def login(self):
        """Handles OAuth login, refresh, and saving new tokens."""
        # Load existing token if available
        if os.path.exists(self.token_path):
            with open(self.token_path, "rb") as token:
                self.creds = pickle.load(token)

        # Authenticate if needed
        if not self.creds or not self.creds.valid:
            if self.creds and self.creds.expired and self.creds.refresh_token:
                self.creds.refresh(Request())
            else:
                # Fresh login using bundled credentials.json
                flow = InstalledAppFlow.from_client_secrets_file(
                    self.credentials_path, SCOPES
                )
                self.creds = flow.run_local_server(port=0)

            # Save new token
            with open(self.token_path, "wb") as token:
                pickle.dump(self.creds, token)

        self.service = build("drive", "v3", credentials=self.creds)

    # -----------------------------------------------------
    # BASIC UPLOAD (existing or new)
    # -----------------------------------------------------
    def upload_file(self, file_path, file_name=None, mime_type="application/octet-stream"):
        if not file_name:
            file_name = os.path.basename(file_path)

        file_metadata = {"name": file_name}
        media = MediaFileUpload(file_path, mimetype=mime_type)

        # Check if file exists already
        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()

        items = results.get("files", [])

        if items:
            # Update existing
            file_id = items[0]["id"]
            updated = self.service.files().update(
                fileId=file_id,
                media_body=media
            ).execute()
            return updated.get("id")

        # Upload new file
        created = self.service.files().create(
            body=file_metadata, media_body=media, fields="id"
        ).execute()
        return created.get("id")

    # -----------------------------------------------------
    # DOWNLOAD THE MOST RECENT BACKUP
    # -----------------------------------------------------
    def download_latest_backup(self, file_name="workouts_backup.db", destination_path="workouts.db"):

        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()

        items = results.get("files", [])
        if not items:
            raise FileNotFoundError(f"No backup named {file_name} found.")

        file_id = items[0]["id"]

        request = self.service.files().get_media(fileId=file_id)
        fh = io.FileIO(destination_path, "wb")
        downloader = MediaIoBaseDownload(fh, request)

        done = False
        while not done:
            status, done = downloader.next_chunk()
            if status:
                print(f"Download {int(status.progress() * 100)}%")

        return destination_path

    # -----------------------------------------------------
    # FOLDER UTILITIES
    # -----------------------------------------------------
    def get_or_create_folder(self, folder_name="Workout Tracker Backups"):
        results = self.service.files().list(
            q=f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder'",
            spaces="drive",
            fields="files(id, name)"
        ).execute()

        items = results.get("files", [])
        if items:
            return items[0]["id"]

        # Create folder if missing
        metadata = {"name": folder_name, "mimeType": "application/vnd.google-apps.folder"}
        folder = self.service.files().create(body=metadata, fields="id").execute()
        return folder.get("id")

    # -----------------------------------------------------
    # UPLOAD TO SPECIFIC FOLDER
    # -----------------------------------------------------
    def upload_to_folder(self, file_path, folder_name="Workout Tracker Backups", mime_type="application/octet-stream"):

        folder_id = self.get_or_create_folder(folder_name)
        file_name = os.path.basename(file_path)

        query = f"name='{file_name}' and '{folder_id}' in parents and trashed=false"
        results = self.service.files().list(q=query, spaces="drive", fields="files(id, name)").execute()

        items = results.get("files", [])
        media = MediaFileUpload(file_path, mimetype=mime_type)

        if items:
            # Update
            updated = self.service.files().update(
                fileId=items[0]["id"],
                media_body=media
            ).execute()
            return updated.get("id")

        # Upload new
        metadata = {"name": file_name, "parents": [folder_id]}
        created = self.service.files().create(
            body=metadata, media_body=media, fields="id"
        ).execute()
        return created.get("id")

    # -----------------------------------------------------
    # DOWNLOAD FILES FROM FOLDER
    # -----------------------------------------------------
    def download_from_folder(self, folder_name="Workout Tracker Backups", local_dir=".", files=None):

        folder_id = self.get_or_create_folder(folder_name)

        results = self.service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()

        items = results.get("files", [])
        if not items:
            raise FileNotFoundError(f"No files found in folder '{folder_name}'")

        downloaded = []

        for item in items:
            if files is None or item["name"] in files:
                file_id = item["id"]
                file_name = item["name"]
                local_path = os.path.join(local_dir, file_name)

                request = self.service.files().get_media(fileId=file_id)
                fh = io.FileIO(local_path, "wb")
                downloader = MediaIoBaseDownload(fh, request)

                done = False
                while not done:
                    status, done = downloader.next_chunk()
                    if status:
                        print(f"Downloading {file_name}: {int(status.progress() * 100)}%")

                downloaded.append(local_path)

        return downloaded
