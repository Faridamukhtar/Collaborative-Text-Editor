
# Collaborative Plain Text Editor

## Overview

This project is a **real-time collaborative plain text editor** developed using **Spring Boot (backend)** and **Vaadin (frontend)**. It enables multiple users to collaboratively edit the same document, with real-time synchronization, cursor tracking, permission control, and CRDT-based consistency. The application supports editor/viewer roles, session-based access via sharable codes, and common file operations like import/export.

## ✨ Features

- **Real-time Collaborative Editing**: Users can simultaneously insert and delete characters in a shared document.
- **Cursor Tracking**: Visual indicators display each user's cursor position to all participants.
- **Undo/Redo Support**: Editors can undo or redo their own recent changes without affecting other users' edits.
- **Import/Export Support**: Upload `.txt` files with preserved formatting and export the current document as a downloadable text file.
- **Sharable Access Codes**: Separate codes for editor (write) and viewer (read-only) access to securely manage collaboration.
- **Permission Management**: Viewers are restricted from editing or accessing sensitive controls (e.g., shareable codes).
- **CRDT Synchronization**: Utilizes a Conflict-Free Replicated Data Type to handle concurrent editing without conflicts.
- **Active User Display**: A real-time list of connected users and their cursor positions is shown in the editor.

## 🛠️ Technology Stack

- **Backend**: Java, Spring Boot, WebSocket
- **Frontend**: Vaadin (Java), Custom JavaScript for editor enhancements
- **Concurrency**: Tree-based CRDT implementation for consistent multi-user editing
- **Build Tools**: Maven

## 🚀 Installation

### Backend Setup (Spring Boot)

1. Clone the repository:
   ```bash
   git clone https://github.com/Faridamukhtar/Collaborative-Text-Editor.git
   ```
2. Navigate to backend:
   ```bash
   cd backend
   ```
3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
4. Server will be available at `http://localhost:8081`

### Frontend Setup (Vaadin)

1. Navigate to frontend:
   ```bash
   cd collaborative-text-editor
   ```
2. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
3. App will be accessible at `http://localhost:8080`

## 💡 Usage

- **Start a Session**: Launch the app, receive a document ID and editor/viewer codes.
- **Share Access**: Distribute the proper code depending on whether the collaborator should be an editor or viewer.
- **Edit Together**: Real-time updates will sync across all clients.
- **Track Cursors**: Colored markers show active cursor positions for all users.
- **Manage Text**: Use import/export functions for `.txt` files and undo/redo for local changes.

## 📂 Project Structure

- `backend/` - Spring Boot project with CRDT, WebSocket endpoints, and session logic.
- `frontend/` - Vaadin app with UI elements, editor logic, and collaboration features.
- `shared/` - (if applicable) common types and logic reused across frontend/backend.
- `crdt/` - Custom-built tree-based CRDT module for handling collaborative edits.

## 📌 Author

Developed by [Farida Mukhtar](https://github.com/Faridamukhtar)  
For educational and experimental use in distributed systems and collaborative editing.

