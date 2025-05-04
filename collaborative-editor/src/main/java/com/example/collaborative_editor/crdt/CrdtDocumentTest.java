// package com.example.collaborative_editor.crdt;

// import com.example.collaborative_editor.backend.crdt.*;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.Assert.*;

// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.List;
// import java.util.UUID;


// import static org.junit.jupiter.api.Assertions.*;

// public class CrdtDocumentTest {
    
//     private CrdtDocument doc1;
//     private CrdtDocument doc2;
    
//     @BeforeEach
//     public void setup() {
//         doc1 = new CrdtDocument("site1");
//         doc2 = new CrdtDocument("site2");
//     }
    
//     @Test
//     public void testInsertChar() {
//         // Create a position
//         List<Identifier> position = Arrays.asList(
//             new Identifier(16, "site1")
//         );
        
//         // Create a character
//         CrdtChar ch = new CrdtChar("a", position, "site1", System.currentTimeMillis());
        
//         // Create an operation
//         Operation op = new Operation(
//             Operation.Type.INSERT, 
//             ch, 
//             "user1", 
//             "session1", 
//             System.currentTimeMillis()
//         );
        
//         // Apply operation
//         doc1.applyOperation(op);
        
//         // Verify
//         assertEquals("a", doc1.toString());
//     }
    
//     @Test
//     public void testDeleteChar() {
//         // Insert character first
//         List<Identifier> position = Arrays.asList(
//             new Identifier(16, "site1")
//         );
        
//         CrdtChar ch = new CrdtChar("a", position, "site1", System.currentTimeMillis());
        
//         Operation insertOp = new Operation(
//             Operation.Type.INSERT, 
//             ch, 
//             "user1", 
//             "session1", 
//             System.currentTimeMillis()
//         );
        
//         doc1.applyOperation(insertOp);
//         assertEquals("a", doc1.toString());
        
//         // Then delete it
//         Operation deleteOp = new Operation(
//             Operation.Type.DELETE, 
//             ch, 
//             "user1", 
//             "session1", 
//             System.currentTimeMillis() + 100
//         );
        
//         doc1.applyOperation(deleteOp);
//         assertEquals("", doc1.toString());
//     }
    
//     @Test
//     public void testConcurrentEdits() {
//         // User 1 inserts 'a' at position [16, site1]
//         List<Identifier> pos1 = Arrays.asList(
//             new Identifier(16, "site1")
//         );
//         CrdtChar ch1 = new CrdtChar("a", pos1, "site1", 100);
//         Operation op1 = new Operation(Operation.Type.INSERT, ch1, "user1", "session1", 100);
        
//         // User 2 inserts 'b' at position [16, site2]
//         List<Identifier> pos2 = Arrays.asList(
//             new Identifier(16, "site2")
//         );
//         CrdtChar ch2 = new CrdtChar("b", pos2, "site2", 100);
//         Operation op2 = new Operation(Operation.Type.INSERT, ch2, "user2", "session2", 100);
        
//         // Apply operations in different order to different documents
//         doc1.applyOperation(op1);
//         doc1.applyOperation(op2);
        
//         doc2.applyOperation(op2);
//         doc2.applyOperation(op1);
        
//         // Both documents should converge to same state
//         // The ordering will be determined by comparing [16, site1] vs [16, site2]
//         // Since site1 < site2 lexicographically, 'a' should come before 'b'
//         assertEquals("ab", doc1.toString());
//         assertEquals("ab", doc2.toString());
//     }
    
//     @Test
//     public void testPositionGeneration() {
//         // Generate position between empty and empty
//         List<Identifier> pos1 = doc1.generatePositionBetween(new ArrayList<>(), new ArrayList<>());
//         assertNotNull(pos1);
//         assertFalse(pos1.isEmpty());
        
//         // Generate position between pos1 and empty
//         List<Identifier> pos2 = doc1.generatePositionBetween(pos1, new ArrayList<>());
//         assertNotNull(pos2);
//         assertTrue(pos2.get(0).getDigit() > pos1.get(0).getDigit());
        
//         // Generate position between empty and pos1
//         List<Identifier> pos3 = doc1.generatePositionBetween(new ArrayList<>(), pos1);
//         assertNotNull(pos3);
//         assertTrue(pos3.get(0).getDigit() < pos1.get(0).getDigit());
        
//         // Generate position between pos3 and pos1
//         List<Identifier> pos4 = doc1.generatePositionBetween(pos3, pos1);
//         assertNotNull(pos4);
//         assertTrue(pos4.get(0).getDigit() > pos3.get(0).getDigit());
//         assertTrue(pos4.get(0).getDigit() < pos1.get(0).getDigit());
//     }
// }