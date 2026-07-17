import { useMemo, useState } from 'react'
import Alert from '@mui/material/Alert'
import FormControl from '@mui/material/FormControl'
import Grid from '@mui/material/Grid'
import InputLabel from '@mui/material/InputLabel'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  getDashboardKpis,
  getLeaderboard,
  getPipelineFunnel,
  getRevenueTimeseries,
  type DateRange,
  type FunnelStage,
} from '../api/dashboard'
import { ApiError, getErrorMessage } from '../api/client'
import { useFormatters } from '../hooks/useFormatters'

const dashboardQueryKey = ['dashboard'] as const

// Farben aus der validierten dataviz-Referenzpalette: eine Serie = ein Blau,
// Gitter/Achsen bewusst zurückhaltend.
const SERIES_COLOR = '#2a78d6'
const GRID_COLOR = '#e1e0d9'
const AXIS_TICK = { fill: '#52514e', fontSize: 12 } as const
const CHART_HEIGHT = 280

const rangePresets = [7, 30, 90] as const
type RangeDays = (typeof rangePresets)[number]

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** 403 nicht erneut versuchen (fehlende Rolle ändert sich nicht), sonst Query-Default. */
function retryUnlessForbidden(failureCount: number, error: unknown): boolean {
  return !(error instanceof ApiError && error.status === 403) && failureCount < 3
}

export function DashboardPage() {
  const { t } = useTranslation()
  const { formatCurrency, formatPercent, formatNumber, formatCompactNumber, formatDate, formatShortDate } =
    useFormatters()
  const [days, setDays] = useState<RangeDays>(30)

  const range = useMemo<DateRange>(() => {
    const from = new Date()
    from.setDate(from.getDate() - (days - 1))
    return { from: toIsoDate(from), to: toIsoDate(new Date()) }
  }, [days])

  const kpisQuery = useQuery({
    queryKey: [...dashboardQueryKey, 'kpis', range],
    queryFn: ({ signal }) => getDashboardKpis(range, signal),
  })
  const timeseriesQuery = useQuery({
    queryKey: [...dashboardQueryKey, 'revenue-timeseries', range],
    queryFn: ({ signal }) => getRevenueTimeseries(range, signal),
  })
  const funnelQuery = useQuery({
    queryKey: [...dashboardQueryKey, 'pipeline-funnel'],
    queryFn: ({ signal }) => getPipelineFunnel(signal),
  })
  const leaderboardQuery = useQuery({
    queryKey: [...dashboardQueryKey, 'leaderboard', range],
    queryFn: ({ signal }) => getLeaderboard(range, signal),
    retry: retryUnlessForbidden,
  })
  // Für sales-rep antwortet das Backend mit 403 — dann wird das Widget schlicht ausgeblendet.
  const leaderboardForbidden =
    leaderboardQuery.error instanceof ApiError && leaderboardQuery.error.status === 403

  // Zeitreihe auf alle Tage des Zeitraums auffüllen, damit Lücken als 0 erscheinen.
  const revenueSeries = useMemo(() => {
    if (!timeseriesQuery.data) {
      return []
    }
    const byDay = new Map(timeseriesQuery.data.map((point) => [point.day, point.revenue]))
    const [year, month, dayOfMonth] = range.from.split('-').map(Number)
    const cursor = new Date(year, month - 1, dayOfMonth)
    const points: { day: string; revenue: number }[] = []
    for (let i = 0; i < days; i += 1) {
      const key = toIsoDate(cursor)
      points.push({ day: key, revenue: byDay.get(key) ?? 0 })
      cursor.setDate(cursor.getDate() + 1)
    }
    return points
  }, [timeseriesQuery.data, range.from, days])

  const funnelData = useMemo(
    () =>
      funnelQuery.data ? [...funnelQuery.data].sort((a, b) => a.sort_order - b.sort_order) : [],
    [funnelQuery.data],
  )

  const kpis = kpisQuery.data
  const kpiTiles = kpis
    ? [
        { key: 'revenue', value: formatCurrency(kpis.revenue) },
        { key: 'wonLost', value: `${kpis.wonCount} / ${kpis.lostCount}` },
        { key: 'winRate', value: formatPercent(kpis.winRate) },
        {
          key: 'avgSalesCycle',
          value: t('dashboard.kpi.daysValue', { value: formatNumber(kpis.avgSalesCycleDays) }),
        },
        { key: 'openPipeline', value: formatCurrency(kpis.openPipeline) },
        { key: 'weightedPipeline', value: formatCurrency(kpis.weightedPipeline) },
        { key: 'activities', value: String(kpis.activitiesCount) },
      ]
    : null

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('dashboard.heading')}
        </Typography>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="dashboard-range-label">{t('dashboard.range.label')}</InputLabel>
          <Select
            labelId="dashboard-range-label"
            label={t('dashboard.range.label')}
            value={days}
            onChange={(event) => setDays(event.target.value as RangeDays)}
          >
            {rangePresets.map((preset) => (
              <MenuItem key={preset} value={preset}>
                {t(`dashboard.range.last${preset}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {kpisQuery.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('dashboard.kpisLoadError', { message: getErrorMessage(kpisQuery.error) })}
        </Alert>
      )}

      <Grid container spacing={2}>
        {kpiTiles
          ? kpiTiles.map((tile) => (
              <Grid item xs={12} sm={6} md={3} key={tile.key}>
                <KpiTile label={t(`dashboard.kpi.${tile.key}`)} value={tile.value} />
              </Grid>
            ))
          : kpisQuery.isPending &&
            Array.from({ length: 7 }, (_, index) => (
              <Grid item xs={12} sm={6} md={3} key={index}>
                <Paper sx={{ p: 2 }}>
                  <Skeleton width="60%" />
                  <Skeleton width="40%" height={32} />
                </Paper>
              </Grid>
            ))}

        <Grid item xs={12} lg={7}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              {t('dashboard.revenueChart.title')}
            </Typography>
            {timeseriesQuery.isPending && (
              <Skeleton variant="rectangular" height={CHART_HEIGHT} />
            )}
            {timeseriesQuery.isError && (
              <Alert severity="error">
                {t('dashboard.revenueChart.loadError', {
                  message: getErrorMessage(timeseriesQuery.error),
                })}
              </Alert>
            )}
            {timeseriesQuery.isSuccess && (
              <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
                <AreaChart data={revenueSeries} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
                  <CartesianGrid stroke={GRID_COLOR} vertical={false} />
                  <XAxis
                    dataKey="day"
                    tick={AXIS_TICK}
                    tickLine={false}
                    axisLine={{ stroke: GRID_COLOR }}
                    tickFormatter={formatShortDate}
                    minTickGap={24}
                  />
                  <YAxis
                    tick={AXIS_TICK}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(value) => formatCompactNumber(Number(value))}
                    width={60}
                  />
                  <Tooltip
                    formatter={(value) => [
                      formatCurrency(Number(value)),
                      t('dashboard.revenueChart.revenue'),
                    ]}
                    labelFormatter={(label) => formatDate(String(label))}
                  />
                  <Area
                    type="monotone"
                    dataKey="revenue"
                    name={t('dashboard.revenueChart.revenue')}
                    stroke={SERIES_COLOR}
                    strokeWidth={2}
                    fill={SERIES_COLOR}
                    fillOpacity={0.15}
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              {t('dashboard.funnelChart.title')}
            </Typography>
            {funnelQuery.isPending && <Skeleton variant="rectangular" height={CHART_HEIGHT} />}
            {funnelQuery.isError && (
              <Alert severity="error">
                {t('dashboard.funnelChart.loadError', {
                  message: getErrorMessage(funnelQuery.error),
                })}
              </Alert>
            )}
            {funnelQuery.isSuccess && (
              <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
                <BarChart
                  data={funnelData}
                  layout="vertical"
                  margin={{ top: 8, right: 16, left: 8, bottom: 0 }}
                >
                  <CartesianGrid stroke={GRID_COLOR} horizontal={false} />
                  <XAxis
                    type="number"
                    tick={AXIS_TICK}
                    tickLine={false}
                    axisLine={{ stroke: GRID_COLOR }}
                    allowDecimals={false}
                  />
                  <YAxis
                    type="category"
                    dataKey="name"
                    tick={AXIS_TICK}
                    tickLine={false}
                    axisLine={false}
                    width={120}
                  />
                  <Tooltip content={<FunnelTooltip />} cursor={{ fill: 'rgba(0, 0, 0, 0.04)' }} />
                  <Bar
                    dataKey="count"
                    name={t('dashboard.funnelChart.count')}
                    fill={SERIES_COLOR}
                    radius={[0, 4, 4, 0]}
                    barSize={18}
                  />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Paper>
        </Grid>

        {!leaderboardForbidden && (
          <Grid item xs={12} lg={7}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle1" gutterBottom>
                {t('dashboard.leaderboard.title')}
              </Typography>
              {leaderboardQuery.isPending && <Skeleton variant="rectangular" height={160} />}
              {leaderboardQuery.isError && (
                <Alert severity="error">
                  {t('dashboard.leaderboard.loadError', {
                    message: getErrorMessage(leaderboardQuery.error),
                  })}
                </Alert>
              )}
              {leaderboardQuery.isSuccess && (
                <TableContainer>
                  <Table size="small" aria-label={t('dashboard.leaderboard.title')}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('dashboard.leaderboard.user')}</TableCell>
                        <TableCell align="right">{t('dashboard.leaderboard.revenue')}</TableCell>
                        <TableCell align="right">{t('dashboard.leaderboard.wonCount')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {leaderboardQuery.data.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={3} align="center">
                            <Typography color="text.secondary" py={2}>
                              {t('dashboard.leaderboard.empty')}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ) : (
                        leaderboardQuery.data.map((entry) => (
                          <TableRow key={entry.user_id} hover>
                            <TableCell>{entry.display_name}</TableCell>
                            <TableCell align="right">{formatCurrency(entry.revenue)}</TableCell>
                            <TableCell align="right">{entry.won_count}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Paper>
          </Grid>
        )}
      </Grid>
    </>
  )
}

interface KpiTileProps {
  label: string
  value: string
}

function KpiTile({ label, value }: KpiTileProps) {
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {label}
      </Typography>
      <Typography variant="h6" component="p">
        {value}
      </Typography>
    </Paper>
  )
}

interface FunnelTooltipProps {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: FunnelStage }>
}

/** Eigener Tooltip für den Funnel: Stage-Name, Anzahl und Beträge. */
function FunnelTooltip({ active, payload }: FunnelTooltipProps) {
  const { t } = useTranslation()
  const { formatCurrency } = useFormatters()
  const stage = payload?.[0]?.payload
  if (!active || !stage) {
    return null
  }
  return (
    <Paper elevation={3} sx={{ p: 1.5 }}>
      <Typography variant="subtitle2">{stage.name}</Typography>
      <Typography variant="body2">
        {t('dashboard.funnelChart.count')}: {stage.count}
      </Typography>
      <Typography variant="body2">
        {t('dashboard.funnelChart.amount')}: {formatCurrency(stage.amount)}
      </Typography>
      <Typography variant="body2">
        {t('dashboard.funnelChart.weighted')}: {formatCurrency(stage.weighted_amount)}
      </Typography>
    </Paper>
  )
}
