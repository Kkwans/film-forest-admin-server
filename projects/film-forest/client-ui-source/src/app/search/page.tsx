'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { searchApi } from '@/lib/api';

interface SearchResult {
  id: number;
  type: 'movie' | 'drama' | 'variety' | 'anime' | 'short_drama';
  title: string;
  cover: string;
  year: number | null;
  rating: number | null;
  summary: string | null;
}

interface SearchPageData {
  records: SearchResult[];
  total: number;
  size: number;
}

function TypeLabel({ type }: { type: string }) {
  const map: Record<string, string> = {
    movie: '🎬 电影',
    drama: '📺 剧集',
    variety: '🎤 综艺',
    anime: '🎭 动漫',
    short_drama: '🎯 短剧',
  };
  return <span>{map[type] || type}</span>;
}

function SearchContent() {
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get('q') || '';
  const [keyword, setKeyword] = useState(initialQuery);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [pageData, setPageData] = useState<SearchPageData | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(20);

  const doSearch = async (kw: string, page: number = 1) => {
    if (!kw.trim()) return;
    setLoading(true);
    setSearched(true);
    setCurrentPage(page);
    try {
      const res = await searchApi.search(kw, { page, size: pageSize }) as any;
      const data = res.data?.data || res.data || {};
      setResults(data.records || []);
      setPageData(data);
    } catch (err) {
      console.error('搜索失败', err);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const q = searchParams.get('q') || '';
    setKeyword(q);
    if (q) {
      doSearch(q, 1);
    } else {
      setSearched(false);
      setResults([]);
    }
  }, [searchParams]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (keyword.trim()) {
      doSearch(keyword.trim(), 1);
      window.history.pushState({}, '', `/search?q=${encodeURIComponent(keyword.trim())}`);
    }
  };

  const totalPages = pageData ? Math.ceil(pageData.total / pageData.size) : 1;

  return (
    <div className="flex flex-col gap-6">
      {/* 搜索表单 */}
      <div>
        <h1 className="text-2xl font-bold text-[var(--text-primary)] mb-4">搜索</h1>
        <form onSubmit={handleSearch} className="flex gap-2">
          <Input
            placeholder="输入电影、剧集、综艺名称..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="flex-1 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)]"
          />
          <button type="submit" className="px-6 py-2 bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white rounded-lg">
            搜索
          </button>
        </form>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-[2/3] rounded-lg bg-[var(--bg-card)] animate-pulse" />
          ))}
        </div>
      ) : searched && results.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-[var(--text-secondary)] text-lg">没有找到「{initialQuery || keyword}」的相关结果</p>
          <p className="text-[var(--text-muted)] text-sm mt-2">试试其他关键词？</p>
        </div>
      ) : results.length > 0 ? (
        <div>
          <p className="text-sm text-[var(--text-secondary)] mb-4">
            找到 {pageData?.total || results.length} 个结果
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {results.map((item) => {
              const href = item.type === 'movie' ? `/movie/${item.id}`
                : item.type === 'drama' ? `/drama/${item.id}`
                : item.type === 'variety' ? `/variety/${item.id}`
                : item.type === 'anime' ? `/anime/${item.id}`
                : `/short/${item.id}`;
              return (
                <Link key={`${item.type}-${item.id}`} href={href} className="group">
                  <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                    <div className="relative aspect-[2/3] overflow-hidden">
                      <img
                        src={item.cover || `https://picsum.photos/seed/${item.id}/300/450`}
                        alt={item.title}
                        className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                      />
                      <Badge className="absolute top-2 right-2 bg-[var(--bg-secondary)]/80 text-[var(--text-primary)] text-xs">
                        <TypeLabel type={item.type} />
                      </Badge>
                      {item.rating != null && (
                        <Badge className="absolute bottom-2 left-2 bg-yellow-500/80 text-white text-xs">
                          ★ {item.rating.toFixed(1)}
                        </Badge>
                      )}
                    </div>
                    <CardContent className="p-3">
                      <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                        {item.title}
                      </p>
                      <p className="text-xs text-[var(--text-muted)] mt-1">
                        {item.year || '未知年份'}
                      </p>
                    </CardContent>
                  </Card>
                </Link>
              );
            })}
          </div>

          {/* 分页 */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <Button
                variant="outline"
                size="sm"
                onClick={() => doSearch(keyword, currentPage - 1)}
                disabled={currentPage <= 1}
              >
                上一页
              </Button>
              <span className="flex items-center text-sm text-[var(--text-secondary)]">
                第 {currentPage} / {totalPages} 页
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => doSearch(keyword, currentPage + 1)}
                disabled={currentPage >= totalPages}
              >
                下一页
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="text-center py-12 text-[var(--text-muted)]">
          输入关键词开始搜索
        </div>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<div className="text-center py-12 text-[var(--text-secondary)]">加载中...</div>}>
      <SearchContent />
    </Suspense>
  );
}
