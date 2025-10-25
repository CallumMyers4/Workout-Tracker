# google_drive_helper.py
from googleapiclient.http import MediaFileUpload, MediaIoBaseDownload
import io
import os
import pickle
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build

SCOPES = ["https://www.googleapis.com/auth/drive.file"]

class GoogleDriveHelper:
    def __init__(self, credentials_path="credentials.json", token_path="token.pickle"):
        self.creds = None
        self.credentials_path = credentials_path
        self.token_path = token_path
        self.service = None
        self.login()

    def login(self):
        if os.path.exists(self.token_path):
            with open(self.token_path, "rb") as token:
                self.creds = pickle.load(token)

        if not self.creds or not self.creds.valid:
            if self.creds and self.creds.expired and self.creds.refresh_token:
                self.creds.refresh(Request())
            else:
                flow = InstalledAppFlow.from_client_secrets_file(self.credentials_path, SCOPES)
                self.creds = flow.run_local_server(port=0)

            with open(self.token_path, "wb") as token:
                pickle.dump(self.creds, token)

        self.service = build("drive", "v3", credentials=self.creds)

    def upload_file(self, file_path, file_name=None, mime_type="application/octet-stream"):
        if not file_name:
            file_name = os.path.basename(file_path)

        file_metadata = {"name": file_name}
        media = MediaFileUpload(file_path, mimetype=mime_type)

        # Check if file already exists on Drive
        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()
        items = results.get("files", [])

        if items:
            # Update the existing file
            file_id = items[0]["id"]
            updated = self.service.files().update(
                fileId=file_id,
                media_body=media
            ).execute()
            return updated.get("id")
        else:
            # Upload new file
            file = self.service.files().create(
                body=file_metadata, media_body=media, fields="id"
            ).execute()
            return file.get("id")
    
    def download_latest_backup(self, file_name="workouts_backup.db", destination_path="workouts.db"):
        """
        Find the most recent backup on Drive and download it.
        """
        results = self.service.files().list(
            q=f"name='{file_name}'",
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()
        items = results.get("files", [])

        if not items:
            raise FileNotFoundError(f"No backup file named {file_name} found in Google Drive.")

        file_id = items[0]["id"]

        request = self.service.files().get_media(fileId=file_id)
        fh = io.FileIO(destination_path, "wb")
        downloader = MediaIoBaseDownload(fh, request)

        done = False
        while not done:
            status, done = downloader.next_chunk()
            if status:
                print(f"Download {int(status.progress() * 100)}%.")

        return destination_path

    def get_or_create_folder(self, folder_name="Workout Tracker Backups"):
        """
        Find a folder by name. If it doesn’t exist, create it.
        """
        results = self.service.files().list(
            q=f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder'",
            spaces="drive",
            fields="files(id, name)",
        ).execute()
        items = results.get("files", [])

        if items:
            return items[0]["id"]  # folder already exists

        # Create folder if not found
        file_metadata = {
            "name": folder_name,
            "mimeType": "application/vnd.google-apps.folder",
        }
        folder = self.service.files().create(body=file_metadata, fields="id").execute()
        return folder.get("id")

    def upload_to_folder(self, file_path, folder_name="Workout Tracker Backups", mime_type="application/octet-stream"):
        """
        Upload or update a file inside a specific Drive folder.
        """
        folder_id = self.get_or_create_folder(folder_name)
        file_name = os.path.basename(file_path)

        # Check if file already exists in the folder
        query = (
            f"name='{file_name}' and "
            f"'{folder_id}' in parents and "
            f"trashed=false"
        )
        results = self.service.files().list(
            q=query,
            spaces="drive",
            fields="files(id, name)"
        ).execute()
        items = results.get("files", [])

        media = MediaFileUpload(file_path, mimetype=mime_type)

        if items:
            # Update the existing file
            file_id = items[0]["id"]
            updated = self.service.files().update(
                fileId=file_id,
                media_body=media
            ).execute()
            return updated.get("id")
        else:
            # Upload new file into the folder
            file_metadata = {"name": file_name, "parents": [folder_id]}
            file = self.service.files().create(
                body=file_metadata, media_body=media, fields="id"
            ).execute()
            return file.get("id")
        
    def download_from_folder(self, folder_name="Workout Tracker Backups", local_dir=".", files=None):
        """
        Download specific files from a Drive folder into local_dir.
        If files=None, download everything in the folder.
        """
        folder_id = self.get_or_create_folder(folder_name)

        # Build query to list files inside the folder
        query = f"'{folder_id}' in parents and trashed=false"
        results = self.service.files().list(
            q=query,
            spaces="drive",
            fields="files(id, name, modifiedTime)",
            orderBy="modifiedTime desc"
        ).execute()
        items = results.get("files", [])

        if not items:
            raise FileNotFoundError(f"No files found in Google Drive folder '{folder_name}'")

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
