import { useEffect, useMemo, useState, type ChangeEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import LinearProgress from '@mui/material/LinearProgress'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Stack from '@mui/material/Stack'
import Step from '@mui/material/Step'
import StepLabel from '@mui/material/StepLabel'
import Stepper from '@mui/material/Stepper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/client'
import {
  LEAD_TARGET_FIELDS,
  executeImportJob,
  getImportJob,
  getMappingSuggestion,
  listImportErrors,
  uploadImportFile,
  validateImportJob,
  type DuplicateStrategy,
  type ImportJob,
  type ImportJobError,
  type ImportJobStatus,
} from '../api/imports'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'

const stepKeys = ['file', 'mapping', 'dryRun', 'execute'] as const
const duplicateStrategies: DuplicateStrategy[] = ['SKIP', 'UPDATE', 'CREATE']

function isRunningStatus(status: ImportJobStatus): boolean {
  return status === 'PENDING' || status === 'VALIDATING' || status === 'RUNNING'
}

export function ImportWizardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [activeStep, setActiveStep] = useState(0)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [mapping, setMapping] = useState<Record<string, string>>({})
  const [mappingInitialized, setMappingInitialized] = useState(false)
  const [duplicateStrategy, setDuplicateStrategy] = useState<DuplicateStrategy>('SKIP')
  // Zählt Läufe (Dry-Run/Execute) hoch, damit Poll- und Fehler-Queries je Lauf frisch starten.
  const [runSeq, setRunSeq] = useState(0)
  const [polling, setPolling] = useState(false)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)

  const headers = useMemo(() => job?.options.headers ?? [], [job])

  const uploadMutation = useMutation({
    mutationFn: uploadImportFile,
    onSuccess: (created) => {
      setJob(created)
      setMapping({})
      setMappingInitialized(false)
      setRunSeq(0)
      setPolling(false)
    },
    onError: (error) => {
      setSnackbar({
        message: t('import.file.uploadError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
    },
  })

  const suggestionQuery = useQuery({
    queryKey: ['import-jobs', job?.id, 'mapping-suggestion'],
    queryFn: ({ signal }) => getMappingSuggestion(job?.id as string, signal),
    enabled: job !== null && !mappingInitialized,
    staleTime: Infinity,
  })
  useEffect(() => {
    if (!mappingInitialized && suggestionQuery.data) {
      setMapping(suggestionQuery.data)
      setMappingInitialized(true)
    }
  }, [mappingInitialized, suggestionQuery.data])

  const effectiveMapping = useMemo(
    () => Object.fromEntries(Object.entries(mapping).filter(([, target]) => target !== '')),
    [mapping],
  )

  const runMutation = useMutation({
    mutationFn: (mode: 'validate' | 'execute') => {
      const request = { mapping: effectiveMapping, options: { duplicateStrategy } }
      return mode === 'validate'
        ? validateImportJob(job?.id as string, request)
        : executeImportJob(job?.id as string, request)
    },
    onSuccess: (started, mode) => {
      setJob(started)
      setRunSeq((seq) => seq + 1)
      setPolling(true)
      setActiveStep(mode === 'validate' ? 2 : 3)
    },
    onError: (error) => {
      setSnackbar({
        message: t('import.run.startError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
    },
  })

  const pollQuery = useQuery({
    queryKey: ['import-jobs', job?.id, 'run', runSeq],
    queryFn: ({ signal }) => getImportJob(job?.id as string, signal),
    enabled: job !== null && polling,
    refetchInterval: 1000,
  })
  useEffect(() => {
    const polled = pollQuery.data
    if (!polled) {
      return
    }
    setJob(polled)
    if (!isRunningStatus(polled.status)) {
      setPolling(false)
      // Nach erfolgreichem Import sind die Leads-Listen veraltet.
      if (polled.mode === 'EXECUTE' && polled.status !== 'FAILED') {
        void queryClient.invalidateQueries({ queryKey: ['leads'] })
      }
    }
  }, [pollQuery.data, queryClient])

  const runFinished =
    job !== null && !polling && runSeq > 0 && !isRunningStatus(job.status)
  const errorsQuery = useQuery({
    queryKey: ['import-jobs', job?.id, 'run', runSeq, 'errors'],
    queryFn: ({ signal }) => listImportErrors(job?.id as string, signal),
    enabled: runFinished && (job?.errorRows ?? 0) > 0,
  })

  const busy = uploadMutation.isPending || runMutation.isPending || polling
  const runSucceeded =
    runFinished && (job?.status === 'COMPLETED' || job?.status === 'COMPLETED_WITH_ERRORS')
  const canNext =
    activeStep === 0
      ? job !== null && !busy
      : activeStep === 1
        ? Object.keys(effectiveMapping).length > 0 && !busy
        : activeStep === 2
          ? runSucceeded && !busy
          : false
  const canBack =
    !busy &&
    (activeStep === 1 || activeStep === 2 || (activeStep === 3 && job?.status === 'FAILED'))
  const executeFinished = activeStep === 3 && runSucceeded && job?.mode === 'EXECUTE'

  const handleNext = () => {
    if (activeStep === 0) {
      setActiveStep(1)
    } else if (activeStep === 1) {
      runMutation.mutate('validate')
    } else if (activeStep === 2) {
      runMutation.mutate('execute')
    }
  }
  const handleBack = () => {
    setActiveStep((step) => (step === 3 ? 1 : step - 1))
  }
  const nextLabel =
    activeStep === 0
      ? t('import.next')
      : activeStep === 1
        ? t('import.mapping.startDryRun')
        : t('import.dryRun.startImport')

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) {
      uploadMutation.mutate(file)
    }
  }

  return (
    <>
      <Typography variant="h5" component="h2" mb={3}>
        {t('import.heading')}
      </Typography>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {stepKeys.map((key) => (
          <Step key={key}>
            <StepLabel>{t(`import.steps.${key}`)}</StepLabel>
          </Step>
        ))}
      </Stepper>

      {pollQuery.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('import.run.statusError', { message: getErrorMessage(pollQuery.error) })}
        </Alert>
      )}

      {activeStep === 0 && (
        <Stack spacing={2} alignItems="flex-start">
          <Typography color="text.secondary">{t('import.file.hint')}</Typography>
          <Button
            component="label"
            variant="contained"
            startIcon={<UploadFileIcon />}
            disabled={uploadMutation.isPending}
          >
            {t('import.file.select')}
            <input type="file" accept=".csv,text/csv" hidden onChange={handleFileChange} />
          </Button>
          {uploadMutation.isPending && (
            <Stack direction="row" spacing={1} alignItems="center">
              <CircularProgress size={20} />
              <Typography>{t('import.file.uploading')}</Typography>
            </Stack>
          )}
          {job && !uploadMutation.isPending && (
            <>
              <Alert severity="success">{t('import.file.uploaded', { name: job.fileName })}</Alert>
              <Typography variant="subtitle2">{t('import.file.headers')}</Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {headers.map((header) => (
                  <Chip key={header} label={header} size="small" />
                ))}
              </Stack>
            </>
          )}
        </Stack>
      )}

      {activeStep === 1 && (
        <Stack spacing={3}>
          {suggestionQuery.isError && (
            <Alert severity="warning">
              {t('import.mapping.suggestionError', {
                message: getErrorMessage(suggestionQuery.error),
              })}
            </Alert>
          )}
          <TableContainer component={Paper}>
            <Table size="small" aria-label={t('import.steps.mapping')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('import.mapping.csvColumn')}</TableCell>
                  <TableCell>{t('import.mapping.targetField')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {headers.map((header) => (
                  <TableRow key={header}>
                    <TableCell>{header}</TableCell>
                    <TableCell sx={{ width: '50%' }}>
                      <Select
                        size="small"
                        fullWidth
                        displayEmpty
                        value={mapping[header] ?? ''}
                        onChange={(event) =>
                          setMapping((prev) => ({ ...prev, [header]: event.target.value }))
                        }
                        inputProps={{
                          'aria-label': t('import.mapping.targetFieldFor', { column: header }),
                        }}
                      >
                        <MenuItem value="">
                          <em>{t('import.mapping.ignore')}</em>
                        </MenuItem>
                        {LEAD_TARGET_FIELDS.map((field) => (
                          <MenuItem key={field} value={field}>
                            {t(`import.fields.${field}`)}
                          </MenuItem>
                        ))}
                      </Select>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <FormControl size="small" sx={{ maxWidth: 320 }}>
            <InputLabel id="duplicate-strategy-label">
              {t('import.mapping.duplicateStrategy')}
            </InputLabel>
            <Select
              labelId="duplicate-strategy-label"
              label={t('import.mapping.duplicateStrategy')}
              value={duplicateStrategy}
              onChange={(event) => setDuplicateStrategy(event.target.value as DuplicateStrategy)}
            >
              {duplicateStrategies.map((strategy) => (
                <MenuItem key={strategy} value={strategy}>
                  {t(`import.mapping.strategies.${strategy}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {Object.keys(effectiveMapping).length === 0 && (
            <Alert severity="info">{t('import.mapping.required')}</Alert>
          )}
        </Stack>
      )}

      {(activeStep === 2 || activeStep === 3) && job && (
        <RunStep
          job={job}
          polling={polling}
          variant={activeStep === 2 ? 'dryRun' : 'execute'}
          errors={errorsQuery.data}
          errorsLoading={errorsQuery.isLoading}
          errorsError={errorsQuery.isError ? errorsQuery.error : null}
        />
      )}

      <Stack direction="row" justifyContent="space-between" mt={4}>
        <Button onClick={handleBack} disabled={!canBack}>
          {t('import.back')}
        </Button>
        {activeStep < 3 ? (
          <Button variant="contained" onClick={handleNext} disabled={!canNext}>
            {nextLabel}
          </Button>
        ) : (
          executeFinished && (
            <Button variant="contained" onClick={() => navigate('/leads')}>
              {t('import.execute.toLeads')}
            </Button>
          )
        )}
      </Stack>

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface RunStepProps {
  job: ImportJob
  polling: boolean
  variant: 'dryRun' | 'execute'
  errors: ImportJobError[] | undefined
  errorsLoading: boolean
  errorsError: unknown
}

function RunStep({ job, polling, variant, errors, errorsLoading, errorsError }: RunStepProps) {
  const { t } = useTranslation()

  if (polling || isRunningStatus(job.status)) {
    const total = job.totalRows
    const value =
      total !== null && total > 0 ? Math.min(100, (job.processedRows / total) * 100) : undefined
    return (
      <Stack spacing={2}>
        <Typography>{t(`import.${variant}.running`)}</Typography>
        <LinearProgress
          variant={value !== undefined ? 'determinate' : 'indeterminate'}
          value={value}
        />
        <Typography color="text.secondary">
          {total !== null
            ? t('import.run.progressWithTotal', { processed: job.processedRows, total })
            : t('import.run.progress', { processed: job.processedRows })}
        </Typography>
      </Stack>
    )
  }

  if (job.status === 'FAILED') {
    return <Alert severity="error">{t('import.run.failed')}</Alert>
  }

  const skippedRows = Number(job.options.skippedRows ?? 0)

  return (
    <Stack spacing={3}>
      <Alert severity={job.status === 'COMPLETED' ? 'success' : 'warning'}>
        {t(
          job.status === 'COMPLETED' ? `import.${variant}.done` : `import.${variant}.doneWithErrors`,
        )}
      </Alert>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
        <ResultCard label={t('import.run.totalRows')} value={job.totalRows ?? job.processedRows} />
        <ResultCard label={t('import.run.errorRows')} value={job.errorRows} />
        {variant === 'execute' && (
          <ResultCard label={t('import.run.skippedRows')} value={skippedRows} />
        )}
      </Stack>
      {job.errorRows > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            {t('import.run.errorsHeading')}
          </Typography>
          {errorsLoading && <CircularProgress size={24} />}
          {errorsError != null && (
            <Alert severity="error">
              {t('import.run.errorsLoadError', { message: getErrorMessage(errorsError) })}
            </Alert>
          )}
          {errors && (
            <TableContainer component={Paper}>
              <Table size="small" aria-label={t('import.run.errorsHeading')}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('import.run.errorColumns.row')}</TableCell>
                    <TableCell>{t('import.run.errorColumns.column')}</TableCell>
                    <TableCell>{t('import.run.errorColumns.code')}</TableCell>
                    <TableCell>{t('import.run.errorColumns.message')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {errors.map((error, index) => (
                    <TableRow key={`${error.rowNumber}-${index}`}>
                      <TableCell>{error.rowNumber}</TableCell>
                      <TableCell>{error.columnName ?? '—'}</TableCell>
                      <TableCell>{error.errorCode}</TableCell>
                      <TableCell>{error.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}
    </Stack>
  )
}

function ResultCard({ label, value }: { label: string; value: number }) {
  return (
    <Card variant="outlined" sx={{ minWidth: 160 }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4" component="p">
          {value}
        </Typography>
      </CardContent>
    </Card>
  )
}
