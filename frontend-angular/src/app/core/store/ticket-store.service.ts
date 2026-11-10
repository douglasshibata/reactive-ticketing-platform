import { Injectable, computed, signal, inject } from '@angular/core';
import { ApiService } from '../services/api.service';
import { WebSocketService } from '../services/websocket.service';
import { Seat, SeatStatus, WebSocketMessage } from '../models/ticket.model';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Injectable({
  providedIn: 'root'
})
export class TicketStore {
  private api = inject(ApiService);
  private ws = inject(WebSocketService);

  // Core Signals: Síncronos, ultrarrápidos para o DOM
  public seats = signal<Seat[]>([
    { id: 'VIP-101', seatNumber: 'A-01', tier: 'VIP', price: 350.00, status: 'AVAILABLE' },
    { id: 'VIP-102', seatNumber: 'A-02', tier: 'VIP', price: 350.00, status: 'AVAILABLE' },
    { id: 'CAM-201', seatNumber: 'B-01', tier: 'CAMAROTE', price: 220.00, status: 'AVAILABLE' },
    { id: 'CAM-202', seatNumber: 'B-02', tier: 'CAMAROTE', price: 220.00, status: 'AVAILABLE' },
    { id: 'PIS-301', seatNumber: 'C-01', tier: 'PISTA_PREMIUM', price: 180.00, status: 'AVAILABLE' },
    { id: 'PIS-302', seatNumber: 'C-02', tier: 'PISTA_PREMIUM', price: 180.00, status: 'AVAILABLE' },
    { id: 'PIS-401', seatNumber: 'D-01', tier: 'PISTA', price: 90.00, status: 'AVAILABLE' },
    { id: 'PIS-402', seatNumber: 'D-02', tier: 'PISTA', price: 90.00, status: 'AVAILABLE' }
  ]);

  public selectedSeat = signal<Seat | null>(null);
  public holdTimeRemaining = signal<number>(600); // 10 minutos (600s)
  public isHolding = signal<boolean>(false);
  public isOnline = signal<boolean>(typeof navigator !== 'undefined' ? navigator.onLine : true);

  // Computed Metrics (Recalculados sem polling)
  public availableCount = computed(() =>
    this.seats().filter(s => s.status === 'AVAILABLE').length
  );

  public lockedCount = computed(() =>
    this.seats().filter(s => s.status === 'LOCKED').length
  );

  public soldCount = computed(() =>
    this.seats().filter(s => s.status === 'SOLD' || s.status === 'CHECKED_IN').length
  );

  public checkedInCount = computed(() =>
    this.seats().filter(s => s.status === 'CHECKED_IN').length
  );

  public totalCapacity = computed(() => this.seats().length);

  public occupancyPercentage = computed(() => {
    const total = this.totalCapacity();
    if (total === 0) return 0;
    return Math.round((this.soldCount() / total) * 100);
  });

  public checkInPercentage = computed(() => {
    const sold = this.soldCount();
    if (sold === 0) return 0;
    return Math.round((this.checkedInCount() / sold) * 100);
  });

  constructor() {
    this.listenToNetworkEvents();
    this.listenToWebSocketUpdates();
  }

  public loadInitialInventory(eventId: number = 1): void {
    this.api.getTickets(eventId).subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.seats.set(data);
        }
      },
      error: (err) => console.warn('Usando catálogo local em memória/fallback:', err)
    });
  }

  private listenToWebSocketUpdates(): void {
    this.ws.getMessages().pipe(takeUntilDestroyed()).subscribe({
      next: (msg: WebSocketMessage) => {
        if (!msg || !msg.type) return;

        switch (msg.type) {
          case 'TICKET_LOCKED':
            this.handleSeatStatusChange(msg.payload?.ticketId || msg.payload?.seatId, 'LOCKED');
            break;
          case 'TICKET_RELEASED':
            this.handleSeatStatusChange(msg.payload?.ticketId || msg.payload?.seatId, 'AVAILABLE');
            break;
          case 'TICKET_SOLD':
            this.handleSeatStatusChange(msg.payload?.ticketId || msg.payload?.seatId, 'SOLD');
            break;
          case 'CHECK_IN_SUCCESS':
            this.handleSeatStatusChange(msg.payload?.ticketId || msg.payload?.seatId, 'CHECKED_IN');
            break;
        }
      }
    });
  }

  public handleSeatStatusChange(seatId: string, status: SeatStatus): void {
    if (!seatId) return;
    this.seats.update(current =>
      current.map(seat => seat.id === seatId ? { ...seat, status } : seat)
    );

    // Se o lugar que tínhamos selecionado foi adquirido/bloqueado por outro
    if (this.selectedSeat()?.id === seatId && status !== 'AVAILABLE' && !this.isHolding()) {
      this.selectedSeat.set(null);
    }
  }

  private listenToNetworkEvents(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        console.log('📡 Rede restabelecida: ONLINE');
        this.isOnline.set(true);
      });
      window.addEventListener('offline', () => {
        console.warn('⚠️ Queda de conexão: OFFLINE');
        this.isOnline.set(false);
      });
    }
  }

  public setHold(seat: Seat, seconds: number = 600): void {
    this.selectedSeat.set(seat);
    this.isHolding.set(true);
    this.holdTimeRemaining.set(seconds);
  }

  public clearHold(): void {
    this.selectedSeat.set(null);
    this.isHolding.set(false);
    this.holdTimeRemaining.set(600);
  }
}
