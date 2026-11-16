import { request } from './client'
export interface SystemStatus {
  service: 'UP'
  database: 'CONNECTED'
  databaseVersion: string
  foundationVersion: string
  checkedAt: string
}
export const getSystemStatus = () => request<SystemStatus>('/system/status')
