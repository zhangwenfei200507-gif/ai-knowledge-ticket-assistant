package com.zwf.aisupport.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {

    private List<T> items;
    private int page;
    private int size;
    private long total;
    private int pageCount;

    public static <T> PageVO<T> of(List<T> items, int page, int size, long total) {
        int safeSize = Math.max(1, size);
        PageVO<T> pageVO = new PageVO<>();
        pageVO.setItems(items);
        pageVO.setPage(Math.max(1, page));
        pageVO.setSize(safeSize);
        pageVO.setTotal(total);
        pageVO.setPageCount((int) Math.max(1, Math.ceil((double) total / (double) safeSize)));
        return pageVO;
    }
}
