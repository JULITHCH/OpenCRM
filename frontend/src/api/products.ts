import { apiFetch } from './client'
import type { PageEnvelope } from './types'

/** Produkt laut ProductController.ProductResponse. */
export interface Product {
  id: string
  sku: string
  name: string
  description: string | null
  category: string | null
  unit: string | null
  listPrice: number
  currency: string
  taxRate: number | null
  active: boolean
  externalId: string | null
  createdAt: string | null
}

export interface ProductCreateRequest {
  sku: string
  name: string
  listPrice: number
  category?: string
}

export interface ProductPatchRequest {
  sku?: string
  name?: string
  listPrice?: number
  category?: string
  active?: boolean
}

export interface ProductListParams {
  q?: string
  cursor?: string
  limit?: number
}

export function listProducts(
  params: ProductListParams = {},
  signal?: AbortSignal,
): Promise<PageEnvelope<Product>> {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.cursor) search.set('cursor', params.cursor)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()
  return apiFetch<PageEnvelope<Product>>(`/api/v1/products${query ? `?${query}` : ''}`, { signal })
}

export function createProduct(request: ProductCreateRequest): Promise<Product> {
  return apiFetch<Product>('/api/v1/products', { method: 'POST', body: request })
}

export function updateProduct(id: string, request: ProductPatchRequest): Promise<Product> {
  return apiFetch<Product>(`/api/v1/products/${id}`, { method: 'PATCH', body: request })
}
