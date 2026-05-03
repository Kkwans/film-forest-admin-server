'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { varietyApi } from '@/lib/api';

interface VarietyItem {
  id: number;
  title: string;
  cover: string;
  year: number;
  region: string;
  rating?: number;
  totalEpisode?: number;
  status?: string;
}

export default function VarietyPage() {
  const [items, setItems] = useState<VarietyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [region, setRegion] = useState('all');
  const [year, setYear] = useState('all');

  useEffect(() => {
    fetchList();
  }, [region, year]);

  const fetchList = async () => {
    setLoading(true);
    try {
      const params: any = { page: 1, size: 20 };
      if (region !== 'all') params.region = region;
      if (year !== 'all') {
        if (year === 'older') params.year = 0;
        else params.year = parseInt(year);
      }
      const res = await varietyApi.list(params) as any;
      const raw = res.data?.data?.records || res.data?.data || res.data || [];
      const list = raw.map((v: any) => ({
        id: v.id,
        title: v.title,
        cover: v.posterUrl,
        year: v.year,
        region: Array.isArray(v.region) ? v.region[0] : (v.region || '未知'),
        rating: v.scoreDouban,
        totalEpisode: v.totalEpisode,
        status: v.status === 1 ? '更新中' : '已完结',
      }));
      setItems(list);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (keyword.trim()) {
      window.location.href = `/search?q=${encodeURIComponent(keyword.trim())}`;
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">综艺</h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">热门综艺节目，追综不停</p>
        </div>
        <form onSubmit={handleSearch} className="flex items-center gap-2">
          <Input
            placeholder="搜索综艺..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-48 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
          />
          <button type="submit" className="px-4 py-2 bg-[var(--accent)] hover:bg-[var(--accent-hover)] text-white rounded-lg text-sm">
            搜索
          </button>
        </form>
      </div>

      <div className="flex flex-wrap gap-3">
        <Select value={region} onValueChange={(v) => setRegion(v || 'all')}>
          <SelectTrigger className="w-32 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectValue placeholder="地区" />
          </SelectTrigger>
          <SelectContent className="bg-[var(--bg-card)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectItem value="all">全部地区</SelectItem>
            <SelectItem value="大陆">大陆</SelectItem>
            <SelectItem value="美国">美国</SelectItem>
            <SelectItem value="日本">日本</SelectItem>
            <SelectItem value="韩国">韩国</SelectItem>
            <SelectItem value="台湾">台湾</SelectItem>
          </SelectContent>
        </Select>
        <Select value={year} onValueChange={(v) => setYear(v || 'all')}>
          <SelectTrigger className="w-32 bg-[var(--bg-primary)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectValue placeholder="年代" />
          </SelectTrigger>
          <SelectContent className="bg-[var(--bg-card)] border-[var(--border-color)] text-[var(--text-primary)]">
            <SelectItem value="all">全部年代</SelectItem>
            <SelectItem value="2026">2026</SelectItem>
            <SelectItem value="2025">2025</SelectItem>
            <SelectItem value="2024">2024</SelectItem>
            <SelectItem value="2023">2023</SelectItem>
            <SelectItem value="older">更早</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {Array.from({ length: 10 }).map((_, i) => (
            <div key={i} className="aspect-[2/3] rounded-lg bg-[var(--bg-card)] animate-pulse" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="text-center py-12 text-[var(--text-secondary)]">暂无综艺数据</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {items.map((v) => (
            <Link key={v.id} href={`/variety/${v.id}`} className="group">
              <Card className="overflow-hidden bg-[var(--bg-card)] border-[var(--border-color)] group-hover:border-[var(--accent)]/50 transition-all">
                <div className="relative aspect-[2/3] overflow-hidden">
                  <img
                    src={v.cover || `https://picsum.photos/seed/v${v.id}/300/450`}
                    alt={v.title}
                    className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
                  />
                  {v.rating && (
                    <Badge className="absolute top-2 right-2 bg-[var(--accent)] text-white text-xs">
                      {v.rating}
                    </Badge>
                  )}
                  {v.status === '更新中' && (
                    <Badge className="absolute top-2 left-2 bg-amber-500 text-white text-xs">
                      更新中
                    </Badge>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-medium text-sm text-[var(--text-primary)] truncate group-hover:text-[var(--accent)] transition-colors">
                    {v.title}
                  </p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    {v.year} · {v.region || '未知'} · {v.totalEpisode || '-'}期
                  </p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}