import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TicketStore } from '../../core/store/ticket-store.service';
import { ApiService } from '../../core/services/api.service';
import { Seat } from '../../core/models/ticket.model';

@Component({
  selector: 'app-event-booking',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
      <div class="flex items-center justify-between pb-4 border-b border-slate-800 mb-6">
        <div>
          <h2 class="text-xl font-bold text-white flex items-center gap-2">
            <span>🎫</span> Seleção Reativa de Ingressos
          </h2>
          <p class="text-xs text-slate-400 mt-1">
            Distribuição em tempo real via Redis SETNX & WebSockets Mutiny. Sem overbooking.
          </p>
        </div>
        <div class="flex items-center gap-3">
          <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            {{ store.availableCount() }} Disponíveis
          </span>
          <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <span class="w-2 h-2 rounded-full bg-amber-400"></span>
            {{ store.lockedCount() }} Em Hold (10m)
          </span>
          <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <span class="w-2 h-2 rounded-full bg-rose-400"></span>
            {{ store.soldCount() }} Vendidos
          </span>
        </div>
      </div>

      <!-- Live Seat Grid -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        @for (seat of store.seats(); track seat.id) {
          <div
            class="relative group rounded-xl p-4 border transition-all duration-200"
            [class.border-emerald-500]="seat.status === 'AVAILABLE'"
            [class.bg-emerald-950/20]="seat.status === 'AVAILABLE'"
            [class.border-amber-500]="seat.status === 'LOCKED'"
            [class.bg-amber-950/20]="seat.status === 'LOCKED'"
            [class.border-rose-800]="seat.status === 'SOLD' || seat.status === 'CHECKED_IN'"
            [class.bg-rose-950/10]="seat.status === 'SOLD' || seat.status === 'CHECKED_IN'"
            [class.opacity-50]="seat.status === 'SOLD' || seat.status === 'CHECKED_IN'"
          >
            <div class="flex justify-between items-start mb-2">
              <span class="font-mono font-bold text-sm text-white">{{ seat.id }}</span>
              <span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded"
                [class.bg-emerald-500/20]="seat.status === 'AVAILABLE'"
                [class.text-emerald-300]="seat.status === 'AVAILABLE'"
                [class.bg-amber-500/20]="seat.status === 'LOCKED'"
                [class.text-amber-300]="seat.status === 'LOCKED'"
                [class.bg-rose-500/20]="seat.status === 'SOLD' || seat.status === 'CHECKED_IN'"
                [class.text-rose-300]="seat.status === 'SOLD' || seat.status === 'CHECKED_IN'"
              >
                {{ seat.status }}
              </span>
            </div>

            <div class="text-xs text-slate-400 mb-1">Setor: <strong class="text-slate-200">{{ seat.tier }}</strong></div>
            <div class="text-xs text-slate-400 mb-3">Assento: <strong class="text-slate-200">{{ seat.seatNumber }}</strong></div>
            <div class="text-sm font-semibold text-white mb-4">
              R$ {{ seat.price?.toFixed(2) }}
            </div>

            <button
              (click)="onSelectSeat(seat)"
              [disabled]="seat.status !== 'AVAILABLE' || loadingSeatId === seat.id"
              class="w-full py-2 px-3 rounded-lg text-xs font-semibold transition-all duration-150 flex items-center justify-center gap-1.5"
              [class.bg-emerald-600]="seat.status === 'AVAILABLE'"
              [class.hover:bg-emerald-500]="seat.status === 'AVAILABLE'"
              [class.text-white]="seat.status === 'AVAILABLE'"
              [class.bg-slate-800]="seat.status !== 'AVAILABLE'"
              [class.text-slate-500]="seat.status !== 'AVAILABLE'"
              [class.cursor-not-allowed]="seat.status !== 'AVAILABLE'"
            >
              @if (loadingSeatId === seat.id) {
                <span class="animate-spin inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full"></span>
                Bloqueando Redis...
              } @else if (seat.status === 'AVAILABLE') {
                <span>🔒</span> Bloquear 10m
              } @else if (seat.status === 'LOCKED') {
                <span>⏳</span> Em Reserva
              } @else {
                <span>🚫</span> Esgotado
              }
            </button>
          </div>
        }
      </div>

      <!-- Reservation Feedback Banner -->
      @if (lastFeedback) {
        <div class="p-3 rounded-lg text-xs font-medium border"
             [class.bg-emerald-500/10]="lastFeedbackSuccess"
             [class.text-emerald-400]="lastFeedbackSuccess"
             [class.border-emerald-500/30]="lastFeedbackSuccess"
             [class.bg-rose-500/10]="!lastFeedbackSuccess"
             [class.text-rose-400]="!lastFeedbackSuccess"
             [class.border-rose-500/30]="!lastFeedbackSuccess">
          {{ lastFeedback }}
        </div>
      }
    </div>
  `
})
export class EventBookingComponent {
  public store = inject(TicketStore);
  private api = inject(ApiService);

  public loadingSeatId: string | null = null;
  public lastFeedback: string | null = null;
  public lastFeedbackSuccess = true;

  onSelectSeat(seat: Seat) {
    if (seat.status !== 'AVAILABLE') return;

    this.loadingSeatId = seat.id;
    this.lastFeedback = null;
    const userId = 'USER-' + Math.floor(1000 + Math.random() * 9000);

    this.api.reserveTicket(seat.id, userId, 'participante@exemplo.com').subscribe({
      next: (res) => {
        this.loadingSeatId = null;
        if (res.success) {
          this.lastFeedback = `✅ Lugar ${seat.id} reservado com sucesso no Redis! Você tem 10 minutos para pagar.`;
          this.lastFeedbackSuccess = true;
          this.store.setHold(seat, 600);
        } else {
          this.lastFeedback = `⚠️ Não foi possível bloquear: ${res.message}`;
          this.lastFeedbackSuccess = false;
        }
      },
      error: (err) => {
        this.loadingSeatId = null;
        if (err.status === 409) {
          this.lastFeedback = `⛔ Concorrência Detectada (HTTP 409): O assento ${seat.id} acabou de ser bloqueado por outro comprador em milissegundos.`;
        } else {
          // Em fallback local, simula sucesso
          this.store.handleSeatStatusChange(seat.id, 'LOCKED');
          this.store.setHold(seat, 600);
          this.lastFeedback = `🔒 Assento ${seat.id} em Hold local temporário (600s).`;
        }
        this.lastFeedbackSuccess = false;
      }
    });
  }
}
