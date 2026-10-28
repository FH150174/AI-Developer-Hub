package com.fhaicl.aidevhub.todo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public enum TodoOrder {
    ADDED("添加顺序"), PRIORITY("优先级（高→低）");

    private final String label;
    TodoOrder(String label) { this.label = label; }
    @Override public String toString() { return label; }

    /** Source indexes preserve the identity of duplicate titles and equal-valued tasks. */
    public List<Integer> indexes(List<TodoItem> items) {
        var indexes = IntStream.range(0, items.size()).boxed();
        if (this == PRIORITY) {
            indexes = indexes.sorted(Comparator.<Integer>comparingInt(index -> items.get(index).priority().ordinal())
                    .thenComparingInt(Integer::intValue));
        }
        return indexes.toList();
    }
}
