# Collaborative-Text-Editor
### Writing Functions (CRDT)
- write
kol ma type bey add node fel local version w beyeb3at operation lel web socket 3ashan ye update el backend version by adding node to CRDT
shakl el op: operation(position, letter, operation, timestamp, userid)

- Read
sync kol editaya fel crdt beta3 el backend by DFSing el CRDT tree
eb3ateeh ka text mesh crdt fel FE
update FE

### Create and join document:
- ./create
wana ba create el server yeb3atly user id w sharing ids (beta3 el view + el edit)
w ye create document w yenady join bel share code beta3 el edit (should be able to take initial document/txt as input -> beysama3 fel crdt beta el backend)

- ./join (betakhod el code beta3 el joining, returns string -> specifies role, edit aw view)
e2fely el edit men el frontend
el mafrood tedkhol existing document (feeh doc map -> current active docs)
editor id is document id and maps to document(editorId, viewerId, activeUsers, hashmap<user, userCursor>)


### Rest of Writing and CRDT
- Undo/Redo
fel FE ne store akher 10 operations (2 stacks) el howa kol el operation(position, letter, operationId - returned from the insert/delete op sent to backend, timestamp, userid) ma3 kol write operation w ba3den law undo/redo ne reverse el operation (e.g., ne set as tombstone fel backend masalan law han delete)


### Document Functions
- active users -> ws bet broadcast awel lama user yedkhol aw yo5rog (onconnect/disconnect)
(check 7ewar ondisconnect: eb3aty user id to remove it from users hashmap)
- Export (EL TEXT EL MAWGOOD NOW BEL DOCUMENT BREAKS W KEDA)

### Reconnection: 
Allow users a period of 5 minutes to reconnect if they experience a network drop. Remote edits missed should be sent to the user on reconnection. Local changes done should be sent after reconnection. 
- Operation Buffer fel Local, when you reconnect send operations and syn with remote CRDT


### MESH BONUS
- Cursor tracking: websocket le position el cursors beta3et el users

### Comments


