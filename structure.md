# Google Docs Replica with Tree CRDT - System Architecture

## Overall Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│                 │         │                 │         │                 │
│    Frontend     │◄────────►     Backend     │◄────────►    Database     │
│   (Browser)     │         │   (Java/Spring) │         │   (PostgreSQL)  │
│                 │         │                 │         │                 │
└────────┬────────┘         └────────┬────────┘         └─────────────────┘
         │                           │
         │                           │
         │                           │
┌────────▼────────┐         ┌────────▼────────┐
│                 │         │                 │
│  WebSocket API  │◄────────►  WebSocket API  │
│   (JavaScript)  │         │     (Java)      │
│                 │         │                 │
└─────────────────┘         └─────────────────┘
```

## Project Structure

```
google-docs-crdt/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/example/gdocs/
│   │   │   │       ├── config/
│   │   │   │       │   ├── SecurityConfig.java
│   │   │   │       │   └── WebSocketConfig.java
│   │   │   │       ├── controller/
│   │   │   │       │   ├── DocumentController.java
│   │   │   │       │   └── AuthController.java
│   │   │   │       ├── crdt/
│   │   │   │       │   ├── TreeCrdt.java
│   │   │   │       │   ├── Operation.java  
│   │   │   │       │   └── DocumentNode.java
│   │   │   │       ├── model/
│   │   │   │       │   ├── Document.java
│   │   │   │       │   └── User.java
│   │   │   │       ├── repository/
│   │   │   │       │   ├── DocumentRepository.java
│   │   │   │       │   └── UserRepository.java
│   │   │   │       ├── service/
│   │   │   │       │   ├── DocumentService.java
│   │   │   │       │   └── UserService.java
│   │   │   │       ├── websocket/
│   │   │   │       │   ├── DocumentWebSocketHandler.java
│   │   │   │       │   ├── WebSocketMessage.java
│   │   │   │       │   └── SessionManager.java
│   │   │   │       └── Application.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── schema.sql
│   │   └── test/
│   └── pom.xml
│
└── frontend/
    ├── public/
    │   ├── index.html
    │   └── favicon.ico
    ├── src/
    │   ├── components/
    │   │   ├── Editor.js
    │   │   ├── Toolbar.js
    │   │   ├── DocumentList.js
    │   │   └── Login.js
    │   ├── crdt/
    │   │   ├── TreeCrdt.js
    │   │   ├── DocumentNode.js
    │   │   └── Operation.js
    │   ├── services/
    │   │   ├── DocumentService.js
    │   │   ├── AuthService.js
    │   │   └── WebSocketService.js
    │   ├── App.js
    │   └── index.js
    ├── package.json
    └── webpack.config.js
```

## Key Components

### 1. Tree CRDT Data Structure

The Tree CRDT (Conflict-free Replicated Data Type) is the core of our collaborative editing system, ensuring that concurrent edits by multiple users can be merged without conflicts.

### 2. WebSocket Communication

WebSockets enable real-time communication between clients and the server, ensuring that changes are propagated immediately.

### 3. Document Model

The document is represented as a tree structure where:
- Each node has a unique identifier
- Operations are applied to specific nodes
- The tree can be traversed to render the document

### 4. User Authentication & Authorization

Standard user authentication with JWT tokens to secure document access and track edits by user.

### 5. Persistence Layer

The document state is periodically persisted to a database for recovery and long-term storage.

## Data Flow

1. User makes an edit in the browser
2. Edit is translated to CRDT operation(s)
3. Operation is applied locally to update the UI immediately
4. Operation is sent via WebSocket to the server
5. Server validates and broadcasts the operation to all connected clients
6. Other clients receive the operation, apply it to their local CRDT, and update their UI
7. Server periodically persists the document state

## Technologies Used

- **Backend**: Java, Spring Boot, Spring WebSocket
- **Frontend**: JavaScript/TypeScript, React
- **Database**: PostgreSQL
- **Build Tools**: Maven (backend), npm/webpack (frontend)