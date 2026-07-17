import { apiFetch } from './client'

/** Zeitraum für Dashboard-Abfragen (jeweils YYYY-MM-DD, inklusiv). */
export interface DateRange {
  from: string
  to: string
}

/** KPIs laut DashboardController.kpis. */
export interface DashboardKpis {
  revenue: number
  wonCount: number
  lostCount: number
  /** Anteil 0..1 */
  winRate: number
  /** Anteil 0..1 */
  leadConversionRate: number
  avgSalesCycleDays: number
  openPipeline: number
  weightedPipeline: number
  activitiesCount: number
}

/** Zeile aus GET /dashboard/revenue-timeseries (SQL-Aliasnamen). */
export interface RevenuePoint {
  day: string
  revenue: number
  won_count: number
}

/** Zeile aus GET /dashboard/pipeline-funnel (SQL-Aliasnamen, nach sort_order sortiert). */
export interface FunnelStage {
  stage_id: string
  name: string
  sort_order: number
  count: number
  amount: number
  weighted_amount: number
}

/** Zeile aus GET /dashboard/leaderboard (403 für sales-rep). */
export interface LeaderboardEntry {
  user_id: string
  display_name: string
  revenue: number
  won_count: number
}

function rangeQuery(range: DateRange): string {
  const search = new URLSearchParams({ from: range.from, to: range.to })
  return `?${search.toString()}`
}

export function getDashboardKpis(range: DateRange, signal?: AbortSignal): Promise<DashboardKpis> {
  return apiFetch<DashboardKpis>(`/api/v1/dashboard/kpis${rangeQuery(range)}`, { signal })
}

export function getRevenueTimeseries(range: DateRange, signal?: AbortSignal): Promise<RevenuePoint[]> {
  return apiFetch<RevenuePoint[]>(`/api/v1/dashboard/revenue-timeseries${rangeQuery(range)}`, {
    signal,
  })
}

export function getPipelineFunnel(signal?: AbortSignal): Promise<FunnelStage[]> {
  return apiFetch<FunnelStage[]>('/api/v1/dashboard/pipeline-funnel', { signal })
}

export function getLeaderboard(range: DateRange, signal?: AbortSignal): Promise<LeaderboardEntry[]> {
  return apiFetch<LeaderboardEntry[]>(`/api/v1/dashboard/leaderboard${rangeQuery(range)}`, {
    signal,
  })
}
