package com.fhaicl.aidevhub.storage;

import java.io.IOException;
import java.nio.file.Path;

public final class HistoryConflictException extends IOException {
    HistoryConflictException(Path first, Path second) {
        super("项目历史数据冲突，所有文件均已保留。请先备份，再核对以下文件，确认后重新加载：\n"
                + first + "\n" + second);
    }

    HistoryConflictException(Path possibleHistory) {
        super("项目目录不存在，无法确认大小写不同的历史记录是否属于同一项目。请恢复目录后重新加载，文件已保留：\n"
                + possibleHistory);
    }

    public static String messageOr(Throwable failure, String fallback) {
        return failure instanceof HistoryConflictException ? failure.getMessage() : fallback;
    }
}
