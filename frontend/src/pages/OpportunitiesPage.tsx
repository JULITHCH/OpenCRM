import { useMemo, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { ChipProps } from '@mui/material/Chip'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  listOpportunities,
  listPipelines,
  type OpportunityStatus,
} from '../api/opportunities'
import { getErrorMessage } from '../api/client'
import { useFormatters } from '../hooks/useFormatters'

const opportunitiesQueryKey = ['opportunities'] as const

const statusColors: Record<OpportunityStatus, ChipProps['color']> = {
  OPEN: 'info',
  WON: 'success',
  LOST: 'error',
}

const allStatuses: OpportunityStatus[] = ['OPEN', 'WON', 'LOST']

/** Nur Anzeige in diesem Inkrement — Anlage/Bearbeitung folgt später. */
export function OpportunitiesPage() {
  const { t } = useTranslation()
  const { formatCurrency, formatDate } = useFormatters()
  const [statusFilter, setStatusFilter] = useState<OpportunityStatus | 'ALL'>('ALL')

  const opportunitiesQuery = useInfiniteQuery({
    queryKey: [...opportunitiesQueryKey, 'list', statusFilter],
    queryFn: ({ pageParam, signal }) =>
      listOpportunities(
        { status: statusFilter === 'ALL' ? undefined : statusFilter, cursor: pageParam },
        signal,
      ),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const opportunities = useMemo(
    () => opportunitiesQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [opportunitiesQuery.data],
  )

  // Stage-Namen über die Pipelines auflösen (stageId → Name).
  const pipelinesQuery = useQuery({
    queryKey: ['pipelines'],
    queryFn: ({ signal }) => listPipelines(signal),
    staleTime: 5 * 60 * 1000,
  })
  const stageNames = useMemo(
    () =>
      new Map<string, string>(
        (pipelinesQuery.data ?? []).flatMap((pipeline) =>
          pipeline.stages.map((stage) => [stage.id, stage.name] as const),
        ),
      ),
    [pipelinesQuery.data],
  )

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('opportunities.heading')}
        </Typography>
      </Stack>

      <FormControl size="small" sx={{ mb: 2, minWidth: 220 }}>
        <InputLabel id="opportunity-status-filter-label">
          {t('opportunities.filter.status')}
        </InputLabel>
        <Select
          labelId="opportunity-status-filter-label"
          label={t('opportunities.filter.status')}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as OpportunityStatus | 'ALL')}
        >
          <MenuItem value="ALL">{t('opportunities.filter.all')}</MenuItem>
          {allStatuses.map((status) => (
            <MenuItem key={status} value={status}>
              {t(`opportunities.status.${status}`)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {opportunitiesQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('opportunities.loading')} />
        </Box>
      )}
      {opportunitiesQuery.isError && (
        <Alert severity="error">
          {t('opportunities.loadError', { message: getErrorMessage(opportunitiesQuery.error) })}
        </Alert>
      )}
      {opportunitiesQuery.isSuccess && (
        <>
          <TableContainer component={Paper}>
            <Table size="small" aria-label={t('opportunities.heading')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('opportunities.columns.name')}</TableCell>
                  <TableCell align="right">{t('opportunities.columns.amount')}</TableCell>
                  <TableCell>{t('opportunities.columns.status')}</TableCell>
                  <TableCell>{t('opportunities.columns.stage')}</TableCell>
                  <TableCell>{t('opportunities.columns.expectedCloseDate')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {opportunities.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography color="text.secondary" py={2}>
                        {t('opportunities.empty')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  opportunities.map((opportunity) => (
                    <TableRow key={opportunity.id} hover>
                      <TableCell>{opportunity.name}</TableCell>
                      <TableCell align="right">
                        {formatCurrency(opportunity.amount, opportunity.currency)}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={statusColors[opportunity.status]}
                          label={t(`opportunities.status.${opportunity.status}`)}
                        />
                      </TableCell>
                      <TableCell>{stageNames.get(opportunity.stageId) ?? '—'}</TableCell>
                      <TableCell>{formatDate(opportunity.expectedCloseDate)}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
          {opportunitiesQuery.hasNextPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Button
                onClick={() => opportunitiesQuery.fetchNextPage()}
                disabled={opportunitiesQuery.isFetchingNextPage}
              >
                {t('common.loadMore')}
              </Button>
            </Box>
          )}
        </>
      )}
    </>
  )
}
