package com.mrzeyner.colonynations.territory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * State land only: claimed chunks mapped to a nation id.
 * City land is derived from MineColonies claims and is not stored here.
 */
public final class TerritoryMap {
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Map<ChunkRef, UUID> ownerByChunk = new HashMap<>();
    private final Map<UUID, Set<ChunkRef>> chunksByNation = new HashMap<>();

    public void claim(ChunkRef chunk, UUID nationId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nationId, "nationId");
        UUID current = ownerByChunk.get(chunk);
        if (nationId.equals(current)) {
            return;
        }
        if (current != null) {
            throw new IllegalStateException("chunk already claimed");
        }
        ownerByChunk.put(chunk, nationId);
        chunksByNation.computeIfAbsent(nationId, id -> new HashSet<>()).add(chunk);
    }

    public void release(ChunkRef chunk) {
        Objects.requireNonNull(chunk, "chunk");
        UUID nationId = ownerByChunk.remove(chunk);
        if (nationId == null) {
            return;
        }
        Set<ChunkRef> chunks = chunksByNation.get(nationId);
        chunks.remove(chunk);
        if (chunks.isEmpty()) {
            chunksByNation.remove(nationId);
        }
    }

    public Optional<UUID> owner(ChunkRef chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return Optional.ofNullable(ownerByChunk.get(chunk));
    }

    public Set<ChunkRef> chunks(UUID nationId) {
        Objects.requireNonNull(nationId, "nationId");
        Set<ChunkRef> chunks = chunksByNation.get(nationId);
        if (chunks == null) {
            return Set.of();
        }
        return Set.copyOf(chunks);
    }

    public int count(UUID nationId) {
        Objects.requireNonNull(nationId, "nationId");
        Set<ChunkRef> chunks = chunksByNation.get(nationId);
        return chunks == null ? 0 : chunks.size();
    }

    /**
     * True when one of the four orthogonal neighbors, in the same dimension, belongs to the nation.
     */
    public boolean isAdjacentToNation(ChunkRef chunk, UUID nationId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nationId, "nationId");
        for (int[] step : NEIGHBORS) {
            if (nationId.equals(ownerByChunk.get(chunk.offset(step[0], step[1])))) {
                return true;
            }
        }
        return false;
    }

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

    public Map<ChunkRef, UUID> claims() {
        return Map.copyOf(ownerByChunk);
    }
}
