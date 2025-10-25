package com.github.hammercroft.m4tchatprogram;

/**
 * A simple immutable container for holding a pair of related objects.
 * <p>
 * This class is typically used to group two values of potentially different
 * types without creating a dedicated structure or record type.
 * Both elements are final and may be {@code null}.
 * </p>
 *
 * @param <A> the type of the first element
 * @param <B> the type of the second element
 */
public class Pair<A, B> {

    private final A first;
    private final B second;

    /**
     * Constructs a new {@code Pair} with the specified elements.
     *
     * @param first  the first element (may be {@code null})
     * @param second the second element (may be {@code null})
     */
    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element of this pair.
     *
     * @return the first element, or {@code null} if none
     */
    public A getFirst() {
        return first;
    }

    /**
     * Returns the second element of this pair.
     *
     * @return the second element, or {@code null} if none
     */
    public B getSecond() {
        return second;
    }
}
