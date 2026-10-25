package com.fhaicl.aidevhub.project;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentProjectTest {
    private final Project first = new Project("First", Path.of("first"));
    private final Project second = new Project("Second", Path.of("second"));

    @Test
    void startsUnselectedAndDoesNotSelectWhenListLoads() {
        CurrentProject current = new CurrentProject();
        current.synchronize(List.of(first, second));
        assertNull(current.project());
    }

    @Test
    void selectsAndSwitchesProject() {
        CurrentProject current = new CurrentProject();
        current.select(first);
        assertEquals(first, current.project());
        current.select(second);
        assertEquals(second, current.project());
        assertNull(new CurrentProject().project());
    }

    @Test
    void synchronizesRenameByPathAndIgnoresOtherProjectChanges() {
        CurrentProject current = new CurrentProject();
        current.select(first);
        current.synchronize(List.of(first));
        assertEquals(first, current.project());
        Project renamed = new Project(second.name(), first.path());
        current.synchronize(List.of(renamed, second));
        assertEquals(renamed, current.project());
    }

    @Test
    void clearsWhenCurrentProjectIsRemoved() {
        CurrentProject current = new CurrentProject();
        current.select(first);
        current.synchronize(List.of(second));
        assertNull(current.project());
    }

    @Test
    void rejectsNullSelectionWithoutChangingCurrentProject() {
        CurrentProject current = new CurrentProject();
        current.select(first);
        assertThrows(NullPointerException.class, () -> current.select(null));
        assertEquals(first, current.project());
    }
}
