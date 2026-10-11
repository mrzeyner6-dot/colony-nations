package com.mrzeyner.colonynations.territory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * State land only: chunk to owning nation. City land is derived from MineColonies claims and is not stored here.
 */
public final class TerritoryMap {
    private final Map<ChunkRef, UUID> ownerByChunk = new HashMap<>();
    private final Map<UUID, Set<ChunkRef>> chunksByNation = new HashMap<>();

    /**
     * Claims wilderness for the nation. Claiming a chunk that nation already owns does nothing.
     */
    public void claim(ChunkRef chunk, UUID nationId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nationId, "nationId");
        UUID existing = ownerByChunk.get(chunk);
        if (nationId.equals(existing)) {
            return;
        }
        if (existing != null) {
            throw new TerritoryException(TerritoryException.Reason.ALREADY_CLAIMED);
        }
        ownerByChunk.put(chunk, nationId);
        chunksByNation.computeIfAbsent(nationId, id -> new HashSet<>()).add(chunk);
    }

    /**
     * Drops the claim. Does nothing when the chunk is wilderness.
     */
    public void release(ChunkRef chunk) {
        Objects.requireNonNull(chunk, "chunk");
        UUID owner = ownerByChunk.remove(chunk);
        if (owner == null) {
            return;
        }
        Set<ChunkRef> chunks = chunksByNation.get(owner);
        chunks.remove(chunk);
        if (chunks.isEmpty()) {
            chunksByNation.remove(owner);
        }
    }

    public Optional<UUID> owner(ChunkRef chunk) {
        if (chunk == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ownerByChunk.get(chunk));
    }

    public Set<ChunkRef> chunks(UUID nationId) {
        Set<ChunkRef> chunks = chunksByNation.get(nationId);
        return chunks == null ? Set.of() : Set.copyOf(chunks);
    }

    /**
     * Every claimed chunk. Persistence uses this; callers that want one nation should use {@link #chunks(UUID)}.
     */
    public Set<ChunkRef> allChunks() {
        return Set.copyOf(ownerByChunk.keySet());
    }

    public int count(UUID nationId) {
        Set<ChunkRef> chunks = chunksByNation.get(nationId);
        return chunks == null ? 0 : chunks.size();
    }

    /**
     * True when one of the four orthogonal neighbours (same dimension) is state land of the nation.
     * Diagonals and other dimensions do not count. The chunk itself does not count.
     */
    public boolean isAdjacentToNation(ChunkRef chunk, UUID nationId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nationId, "nationId");
        return nationId.equals(ownerByChunk.get(east(chunk)))
                || nationId.equals(ownerByChunk.get(west(chunk)))
                || nationId.equals(ownerByChunk.get(south(chunk)))
                || nationId.equals(ownerByChunk.get(north(chunk)));
    }

    /**
     * Drops every state-land chunk of the nation. Used when the nation is disbanded.
     */
    public void releaseAll(UUID nationId) {
        Objects.requireNonNull(nationId, "nationId");
        Set<ChunkRef> chunks = chunksByNation.remove(nationId);
        if (chunks == null) {
            return;
        }
        for (ChunkRef chunk : chunks) {
            ownerByChunk.remove(chunk);
        }
    }

    private static ChunkRef east(ChunkRef chunk) {
        return new ChunkRef(chunk.dimension(), chunk.x() + 1, chunk.z());
    }

    private static ChunkRef west(ChunkRef chunk) {
        return new ChunkRef(chunk.dimension(), chunk.x() - 1, chunk.z());
    }

    private static ChunkRef south(ChunkRef chunk) {
        return new ChunkRef(chunk.dimension(), chunk.x(), chunk.z() + 1);
    }

    private static ChunkRef north(ChunkRef chunk) {
        return new ChunkRef(chunk.dimension(), chunk.x(), chunk.z() - 1);
    }
}
