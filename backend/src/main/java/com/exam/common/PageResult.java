package com.exam.common;

import lombok.Data;
import org.springframework.data.domain.Page;
import java.util.List;

@Data
public class PageResult<T> {

    private List<T> content;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> r = new PageResult<>();
        r.content = page.getContent();
        r.total = page.getTotalElements();
        r.page = page.getNumber();
        r.size = page.getSize();
        r.totalPages = page.getTotalPages();
        return r;
    }
}
