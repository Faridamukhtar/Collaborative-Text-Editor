# Collaborative Plain Text Editor

## Overview

This project is a **simplified real-time collaborative plain text editor** designed and implemented using **Spring Boot** for the backend and **Vaadin** for the frontend. The editor allows multiple users to simultaneously edit the same document, track each user's cursor position, and manage collaborative sessions through shareable codes. The application supports both editing and viewing modes, where editors can modify the document, and viewers can only read.

## Features

* **Real-time Collaborative Editing**: Multiple users can edit the same document in real-time, with character-by-character insertion and deletion support.
* **Cursor Tracking**: Each user's cursor position is tracked and displayed, allowing others to see where they are typing.
* **Undo/Redo**: Users can undo/redo their last actions, but changes made by other users cannot be undone.
* **File Import/Export**: Import and export text files while preserving line breaks.
* **Sharable Codes**: Documents have two codes—one for editors (full access) and one for viewers (read-only access)—which can be shared to start collaborative sessions.
* **Conflict-Free Replicated Data Type (CRDT)**: Utilizes a CRDT algorithm to handle concurrent edits and conflicts.
* **Permission Management**: Viewers cannot edit the text or access sharable codes.
* **Active User List**: Displays a list of users currently in the document and their cursor positions.


### Installation

#### Backend Installation (Spring Boot)

1. Clone the repository:

   ```bash
   git clone https://github.com/Faridamukhtar/Collaborative-Text-Editor.git
   ```

2. Navigate to the backend project directory:

   ```bash
   cd ./backend
   ```

3. Install dependencies and build the project:

   ```bash
   mvn clean install
   ```

4. Run the backend application:

   ```bash
   mvn spring-boot:run
   ```

5. The backend server will be running at `http://localhost:8081`.

#### Frontend Installation (Vaadin)

1. Navigate to the frontend project directory:

   ```bash
   cd ./collaborative-text-editor
   ```

2. Install the frontend dependencies:

   ```bash
    mvn clean install
   ```

3. Run the frontend development server:

   ```bash
      mvn spring-boot:run
   ```

4. The frontend application will be running at `http://localhost:8080`.

### Usage

1. **Starting a Collaboration Session**:

   * After opening the editor, you can share the session with others by sending the editor or viewer code.
   * Editors can modify the document, while viewers will only have read-only access.

2. **Collaborating in Real-Time**:

   * As multiple users type simultaneously, the changes are reflected in real-time for all users connected to the session.
   * The cursor positions of active users are tracked and displayed in the editor.

3. **File Operations**:

   * You can import a text file into the editor, and it will preserve the line breaks.
   * The document can also be exported as a `.txt` file.

4. **Undo/Redo**:

   * Editors can undo or redo their latest actions. However, actions made by other users cannot be undone.

## Technical Details

* **Backend**: Java **Spring Boot**, WebSocket-based communication for real-time collaboration.
* **Frontend**: **Vaadin** framework for UI components, custom JavaScript for handling editor features.
* **Concurrency Handling**: Uses a **CRDT** (Conflict-Free Replicated Data Type) algorithm to ensure consistency across edits.

