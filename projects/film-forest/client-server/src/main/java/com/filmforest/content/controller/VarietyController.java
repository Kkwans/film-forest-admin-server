package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.service.VarietyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 综艺 Controller
 */
@RestController
@RequestMapping("/api/varieties")
public class VarietyController {

    @Autowired
    private VarietyService varietyService;

    /**
     * 综艺列表（分页）
     */
    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre) {
        return Result.ok(varietyService.pageList(page, size, year, region, genre));
    }

    /**
     * 综艺详情
     */
    @GetMapping("/{id}")
    public Result<Variety> detail(@PathVariable Long id) {
        Variety variety = varietyService.getDetail(id);
        return variety != null ? Result.ok(variety) : Result.fail("综艺不存在");
    }

    /**
     * 新增综艺（管理端）
     */
    @PostMapping
    public Result<?> add(@RequestBody Variety variety) {
        varietyService.save(variety);
        return Result.ok();
    }

    /**
     * 更新综艺（管理端）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Variety variety) {
        variety.setId(id);
        varietyService.updateById(variety);
        return Result.ok();
    }

    /**
     * 删除综艺（管理端）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        varietyService.removeById(id);
        return Result.ok();
    }
}
