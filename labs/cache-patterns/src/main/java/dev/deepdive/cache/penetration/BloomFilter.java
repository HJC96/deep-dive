package dev.deepdive.cache.penetration;

import java.util.BitSet;

/**
 * 아주 단순한 Bloom filter.
 *
 * <p>{@link #mightContain}이 false면 그 키는 "확실히 없음"이므로 저장소 조회 자체를 건너뛸 수 있다.
 * true면 "있을 수도 있음"(false positive 가능)이라 저장소 확인이 필요하다. Cache Penetration
 * 방어에서, 존재하지 않는 키 요청을 저장소 앞단에서 잘라낼 때 쓴다.
 */
public final class BloomFilter<T> {

    private final BitSet bits;
    private final int bitSize;
    private final int hashCount;

    public BloomFilter(int bitSize, int hashCount) {
        if (bitSize <= 0 || hashCount <= 0) {
            throw new IllegalArgumentException("bitSize/hashCount must be positive");
        }
        this.bits = new BitSet(bitSize);
        this.bitSize = bitSize;
        this.hashCount = hashCount;
    }

    public void add(T value) {
        for (int seed = 0; seed < hashCount; seed++) {
            bits.set(indexFor(value, seed));
        }
    }

    public boolean mightContain(T value) {
        for (int seed = 0; seed < hashCount; seed++) {
            if (!bits.get(indexFor(value, seed))) {
                return false;
            }
        }
        return true;
    }

    private int indexFor(T value, int seed) {
        int h = value.hashCode();
        h = h * 0x9e3779b1 + (seed * 0x85ebca6b);
        h ^= (h >>> 16);
        return Math.floorMod(h, bitSize);
    }
}
