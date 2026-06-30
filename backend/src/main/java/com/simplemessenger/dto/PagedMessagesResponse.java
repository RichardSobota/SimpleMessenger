package com.simplemessenger.dto;

import java.util.List;

/**
 * Paginated envelope returned by the message listing endpoints.
 *
 * <ul>
 *   <li>{@code data} — the messages on the current page (at most {@code pageSize} entries)</li>
 *   <li>{@code page} — zero-based page index (mirrors the request parameter)</li>
 *   <li>{@code pageSize} — maximum items per page (mirrors the request parameter)</li>
 *   <li>{@code totalElements} — total number of messages across all pages</li>
 *   <li>{@code totalPages} — {@code ceil(totalElements / pageSize)}, or {@code 0} when
 *       {@code totalElements == 0}</li>
 * </ul>
 */
public class PagedMessagesResponse {

    private List<MessageResponse> data;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public PagedMessagesResponse() {}

    public PagedMessagesResponse(List<MessageResponse> data, int page, int pageSize,
                                  long totalElements, int totalPages) {
        this.data = data;
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public List<MessageResponse> getData() {
        return data;
    }

    public void setData(List<MessageResponse> data) {
        this.data = data;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
