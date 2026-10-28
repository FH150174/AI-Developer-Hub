package com.fhaicl.aidevhub.todo;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TodoOrderTest {
    @Test
    void sortsByPriorityAndKeepsSamePriorityInAddedOrder() {
        var items = List.of(new TodoItem("low", false, TodoItem.Priority.LOW),
                new TodoItem("high one", true, TodoItem.Priority.HIGH),
                new TodoItem("medium", false, TodoItem.Priority.MEDIUM),
                new TodoItem("high two", false, TodoItem.Priority.HIGH));
        assertEquals(List.of(1, 3, 2, 0), TodoOrder.PRIORITY.indexes(items));
        assertEquals(List.of(0, 1, 2, 3), TodoOrder.ADDED.indexes(items));
        assertEquals("low", items.getFirst().title());
    }

    @Test
    void preservesDistinctSourceIndexesForIdenticalTasksAndHandlesEmpty() {
        var same = new TodoItem("same");
        assertEquals(List.of(0, 1), TodoOrder.PRIORITY.indexes(List.of(same, same)));
        assertTrue(TodoOrder.PRIORITY.indexes(List.of()).isEmpty());
    }
}
