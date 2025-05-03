import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Operation.Builder.class)
public class Operation implements Serializable {

    public enum Type {
        INSERT, UPDATE, DELETE
    }

    private final Type type;
    private final String nodeId;
    private final String parentId;
    private final int position;
    private final String content;
    private final String userId;
    private final long timestamp;

    private Operation(Builder builder) {
        this.type = builder.type;
        this.nodeId = builder.nodeId;
        this.parentId = builder.parentId;
        this.position = builder.position;
        this.content = builder.content;
        this.userId = builder.userId;
        this.timestamp = builder.timestamp;
    }

    // ✅ Getters (needed by Jackson)
    public Type getType() { return type; }
    public String getNodeId() { return nodeId; }
    public String getParentId() { return parentId; }
    public int getPosition() { return position; }
    public String getContent() { return content; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }

    @JsonPOJOBuilder(withPrefix = "") // no `with` or `set` prefixes
    public static class Builder {
        private Type type;
        private String nodeId;
        private String parentId;
        private int position;
        private String content;
        private String userId;
        private long timestamp;

        public Builder type(Type type) { this.type = type; return this; }
        public Builder nodeId(String nodeId) { this.nodeId = nodeId; return this; }
        public Builder parentId(String parentId) { this.parentId = parentId; return this; }
        public Builder position(int position) { this.position = position; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }

        public Operation build() {
            // optional default timestamp logic
            if (this.timestamp == 0) this.timestamp = System.currentTimeMillis();
            return new Operation(this);
        }
    }
}
