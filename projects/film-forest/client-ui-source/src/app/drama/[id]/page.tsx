'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { Badge } from '@/components/ui/badge';
import { dramaApi } from '@/lib/api';
import { useResource } from '@/hooks/useResource';

interface DramaDetail {
  id: number;
  title: string;
  cover: string;
  year: number;
  region: string;
  rating?: number;
  summary?: string;
  status?: string;
  totalEpisode?: number;
  currentEpisode?: number;
  genre?: string[];
  director?: string[];
  actor?: string[];
}

export default function DramaDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const [drama, setDrama] = useState<DramaDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'info' | 'episode'>('info');

  const { onlineResources: realOnline, loading: resourcesLoading } = useResource('drama', id);

  const [selectedEpisode, setSelectedEpisode] = useState<number | null>(null);
  const { onlineResources: epOnline, magnetResources: epMagnet } = useResource('drama', id, selectedEpisode || undefined);

  useEffect(() => {
    if (id) fetchDetail();
  }, [id]);

  const fetchDetail = async () => {
    setLoading(true);
    try {
      const res = await dramaApi.detail(id) as any;
      const d = res.data?.data || res.data;
      if (d && d.id) {
        setDrama({
          id: d.id,
          title: d.title,
          cover: d.posterUrl,
          year: d.year,
          region: Array.isArray(d.region) ? d.region[0] : (d.region || '未知'),
          rating: d.scoreDouban,
          summary: d.storyline,
          status: d.status === 1 ? '更新中' : '已完结',
          totalEpisode: d.totalEpisode,
          currentEpisode: d.currentEpisode,
          genre: d.genre,
          director: d.director,
          actor: d.actor,
        });
      } else {
        setDrama(null);
      }
    } catch {
      setDrama(null);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6 animate-pulse">
        <div className="flex flex-col sm:flex-row gap-4 sm:gap-6">
          <div className="w-full sm:w-48 md:w-64 aspect-[2/3] rounded-xl bg-[var(--bg-card)] max-w-[192px] mx-auto sm:mx-0" />
          <div className="flex-1 flex flex-col gap-4 pt-2 sm:pt-0">
            <div className="h-8 w-48 bg-[var(--bg-card)] rounded" />
            <div className="h-4 w-32 bg-[var(--bg-card)] rounded" />
            <div className="h-20 bg-[var(--bg-card)] rounded mt-4" />
          </div>
        </div>
      </div>
    );
  }

  if (!drama) {
    return (
      <div className="text-center py-12">
        <p className="text-[var(--text-secondary)]">剧集不存在</p>
        <Link href="/drama" className="text-[var(--accent)] hover:underline mt-4 inline-block">
          返回剧集列表
        </Link>
      </div>
    );
  }

  const mockEpisodes = Array.from({ length: drama.totalEpisode || 12 }, (_, i) => ({
    episode: i + 1,
    title: `第${i + 1}集`,
    status: '可播放',
  }));

  const displayOnline = selectedEpisode ? epOnline : realOnline;

  return (
    <div className="flex flex-col gap-6">
      {/* Back */}
      <Link href="/drama" className="text-sm text-[var(--text-secondary)] hover:text-[var(--accent)] flex items-center gap-1">
        ← 返回剧集列表
      </Link>

      {/* Header */}
      <div className="flex flex-col sm:flex-row gap-6">
        <div className="w-full sm:w-40 sm:w-48 md:w-64 flex-shrink-0 mx-auto sm:mx-0 max-w-[256px]">
          <img
            src={drama.cover || `https://picsum.photos/seed/d${drama.id}/400/600`}
            alt={drama.title}
            className="w-full aspect-[2/3] object-cover rounded-xl"
          />
        </div>
        <div className="flex-1 flex flex-col gap-3 min-w-0">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl sm:text-2xl font-bold text-[var(--text-primary)]">{drama.title}</h1>
            {drama.status && (
              <Badge className={drama.status === '更新中' ? 'bg-amber-500 text-white' : 'bg-emerald-600 text-white'}>
                {drama.status}
              </Badge>
            )}
          </div>
          <div className="flex flex-wrap gap-3 text-sm text-[var(--text-secondary)]">
            <span>{drama.year}年</span>
            <span>{drama.region}</span>
            <span>{drama.totalEpisode || '-'}集</span>
            {drama.rating && <span className="text-[var(--accent)] font-medium">评分 {drama.rating}</span>}
          </div>
          {drama.genre && drama.genre.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {drama.genre.map((g, i) => (
                <Badge key={i} variant="outline" className="border-[var(--border-color)] text-[var(--text-secondary)]">
                  {g}
                </Badge>
              ))}
            </div>
          )}
          {drama.director && drama.director.length > 0 && (
            <div className="text-sm text-[var(--text-secondary)]">
              <span className="font-medium">导演:</span> {drama.director.join(' / ')}
            </div>
          )}
          {drama.actor && drama.actor.length > 0 && (
            <div className="text-sm text-[var(--text-secondary)]">
              <span className="font-medium">主演:</span> {drama.actor.join(' / ')}
            </div>
          )}
          {drama.summary && (
            <p className="text-sm text-[var(--text-secondary)] mt-2 line-clamp-3">{drama.summary}</p>
          )}
        </div>
      </div>

      <div className="border-b border-[var(--border-color)]">
        <div className="flex gap-6 overflow-x-auto">
          {['info', 'episode'].map((tab) => (
            <button
              key={tab}
              onClick={() => { setActiveTab(tab as typeof activeTab); setSelectedEpisode(null); }}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab
                  ? 'border-[var(--accent)] text-[var(--accent)]'
                  : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              {tab === 'info' ? '详情' : '剧集列表'}
            </button>
          ))}
        </div>
      </div>

      {/* Tab Content */}
      {activeTab === 'info' ? (
        <div className="space-y-4">
          <h3 className="font-medium text-[var(--text-primary)]">剧情简介</h3>
          <p className="text-sm text-[var(--text-secondary)] leading-relaxed">
            {drama.summary || '暂无剧情简介'}
          </p>
        </div>
      ) : (
        <div>
          <h3 className="font-medium text-[var(--text-primary)] mb-4">全部剧集 ({drama.totalEpisode || 0}集)</h3>
          <div className="grid grid-cols-3 sm:grid-cols-5 md:grid-cols-8 lg:grid-cols-12 gap-2">
            {mockEpisodes.map((ep) => (
              <button
                key={ep.episode}
                onClick={() => setSelectedEpisode(selectedEpisode === ep.episode ? null : ep.episode)}
                className={`px-3 py-2 rounded-lg text-sm text-[var(--text-primary)] transition-all ${
                  selectedEpisode === ep.episode
                    ? 'bg-[var(--accent)] text-white border border-[var(--accent)]'
                    : 'bg-[var(--bg-card)] border border-[var(--border-color)] hover:border-[var(--accent)]'
                }`}
              >
                {ep.episode}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Resources */}
      <div className="mt-6">
        <h3 className="font-medium text-[var(--text-primary)] mb-4">
          {selectedEpisode ? `第${selectedEpisode}集 播放源` : '在线播放'}
        </h3>
        {resourcesLoading || (selectedEpisode && epOnline.length === 0) ? (
          <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {[1, 2].map((i) => (
              <div key={i} className="h-12 rounded-lg bg-[var(--bg-card)] animate-pulse" />
            ))}
          </div>
        ) : displayOnline.length === 0 ? (
          <p className="text-[var(--text-secondary)] text-sm text-center py-8">
            {selectedEpisode ? '该集暂无资源，请尝试其他集数' : '暂无在线播放资源'}
          </p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {displayOnline.map((res) => (
              <a
                key={res.id}
                href={res.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-between px-4 py-3 rounded-lg bg-[var(--bg-card)] border border-[var(--border-color)] hover:border-[var(--accent)] transition-all cursor-pointer"
              >
                <span className="text-sm font-medium text-[var(--text-primary)]">{res.sourceName}</span>
                <Badge variant="outline" className="border-[var(--border-color)] text-[var(--text-muted)] text-xs">播放</Badge>
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}