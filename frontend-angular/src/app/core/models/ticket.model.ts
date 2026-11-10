export type SeatStatus = 'AVAILABLE' | 'LOCKED' | 'SOLD' | 'CHECKED_IN';

export interface Seat {
  id: string;
  status: SeatStatus;
  seatNumber?: string;
  tier?: string;
  price?: number;
  lockedBy?: string;
  lockedUntil?: string;
  ownerName?: string;
}

export interface SeatStatusEvent {
  ticketId?: string;
  seatId?: string;
  status: SeatStatus;
  lockedBy?: string;
  lockedUntil?: string;
  ownerName?: string;
  timestamp?: string;
}

export interface WebSocketMessage {
  type: string;
  message: string;
  payload?: any;
}

export interface CheckInItem {
  id?: number;
  ticketId: string;
  scannedAt: string;
  gate: string;
  staffUserId: string;
  synced?: boolean;
}
