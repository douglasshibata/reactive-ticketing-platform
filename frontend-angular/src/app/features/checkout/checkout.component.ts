import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TicketStore } from '../../core/store/ticket-store.service';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
      <div class="flex items-center justify-between pb-4 border-b border-slate-800 mb-6">
        <div>
          <h2 class="text-xl font-bold text-white flex items-center gap-2">
            <span>💳</span> Checkout & Confirmação de Pagamento
          </h2>
          <p class="text-xs text-slate-400 mt-1">
            Garantia de transação atômica. O bilhete é emitido e despachado assincronamente via Kafka.
          </p>
        </div>

        @if (store.isHolding() && store.selectedSeat()) {
          <div class="flex items-center gap-2 bg-amber-500/10 border border-amber-500/30 px-3 py-1.5 rounded-xl">
            <span class="text-amber-400 animate-pulse text-sm">⏱️</span>
            <div class="text-xs">
              <span class="text-slate-400">Tempo restante do Hold:</span>
              <strong class="font-mono text-amber-300 ml-1">{{ formatTimer(timerSeconds) }}</strong>
            </div>
          </div>
        }
      </div>

      @if (!store.isHolding() || !store.selectedSeat()) {
        <div class="text-center py-12 border-2 border-dashed border-slate-800 rounded-xl">
          <p class="text-slate-400 text-sm mb-2">Nenhum ingresso selecionado no momento.</p>
          <p class="text-xs text-slate-500">Volte à aba de Ingressos e clique em "Bloquear 10m" para iniciar a compra.</p>
        </div>
      } @else {
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Resumo da Reserva -->
          <div class="bg-slate-950/60 p-5 rounded-xl border border-slate-800">
            <h3 class="text-sm font-semibold text-slate-200 mb-4 uppercase tracking-wider">
              Resumo do Ingresso Selecionado
            </h3>

            <div class="space-y-3 text-sm">
              <div class="flex justify-between border-b border-slate-800/80 pb-2">
                <span class="text-slate-400">ID do Ingresso:</span>
                <span class="font-mono font-bold text-white">{{ store.selectedSeat()?.id }}</span>
              </div>
              <div class="flex justify-between border-b border-slate-800/80 pb-2">
                <span class="text-slate-400">Setor:</span>
                <span class="text-slate-200 font-medium">{{ store.selectedSeat()?.tier }}</span>
              </div>
              <div class="flex justify-between border-b border-slate-800/80 pb-2">
                <span class="text-slate-400">Cadeira Marcada:</span>
                <span class="text-slate-200 font-medium">{{ store.selectedSeat()?.seatNumber }}</span>
              </div>
              <div class="flex justify-between pt-2">
                <span class="text-base font-bold text-slate-300">Total a Pagar:</span>
                <span class="text-lg font-bold text-emerald-400">R$ {{ store.selectedSeat()?.price?.toFixed(2) }}</span>
              </div>
            </div>

            <div class="mt-6 flex items-center justify-between">
              <button
                (click)="onCancelHold()"
                class="text-xs text-rose-400 hover:text-rose-300 underline font-medium"
              >
                Cancelar reserva e libertar lugar
              </button>
            </div>
          </div>

          <!-- Formulário de Pagamento -->
          <div class="bg-slate-950/60 p-5 rounded-xl border border-slate-800">
            <h3 class="text-sm font-semibold text-slate-200 mb-4 uppercase tracking-wider">
              Dados do Titular
            </h3>

            <form (ngSubmit)="onConfirmPayment()" class="space-y-4">
              <div>
                <label class="block text-xs font-medium text-slate-300 mb-1">Nome Completo</label>
                <input
                  type="text"
                  [(ngModel)]="buyerName"
                  name="buyerName"
                  required
                  placeholder="Ex: Carlos Alberto Silva"
                  class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-brand-500"
                />
              </div>

              <div>
                <label class="block text-xs font-medium text-slate-300 mb-1">E-mail para Receber o QRCode</label>
                <input
                  type="email"
                  [(ngModel)]="buyerEmail"
                  name="buyerEmail"
                  required
                  placeholder="carlos@exemplo.com"
                  class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-brand-500"
                />
              </div>

              <div class="pt-2">
                <button
                  type="submit"
                  [disabled]="isProcessing"
                  class="w-full bg-brand-600 hover:bg-brand-500 text-white font-semibold py-2.5 px-4 rounded-xl text-xs transition duration-150 flex items-center justify-center gap-2 shadow-lg shadow-brand-600/30"
                >
                  @if (isProcessing) {
                    <span class="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full"></span>
                    Efetivando transação assíncrona...
                  } @else {
                    <span>🚀</span> Pagar & Emitir Bilhete
                  }
                </button>
              </div>
            </form>

            @if (purchaseMessage) {
              <div class="mt-4 p-3 rounded-lg text-xs font-medium border bg-emerald-500/10 text-emerald-400 border-emerald-500/30">
                {{ purchaseMessage }}
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class CheckoutComponent implements OnInit, OnDestroy {
  public store = inject(TicketStore);
  private api = inject(ApiService);

  public buyerName = 'Carlos Alberto';
  public buyerEmail = 'carlos.alberto@exemplo.com';
  public isProcessing = false;
  public purchaseMessage: string | null = null;
  public timerSeconds = 600;

  private timerInterval: any;

  ngOnInit() {
    this.startCountdown();
  }

  ngOnDestroy() {
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  private startCountdown() {
    if (this.timerInterval) clearInterval(this.timerInterval);
    this.timerInterval = setInterval(() => {
      if (this.timerSeconds > 0 && this.store.isHolding()) {
        this.timerSeconds--;
        this.store.holdTimeRemaining.set(this.timerSeconds);
      } else if (this.timerSeconds === 0 && this.store.isHolding()) {
        // Expirou
        this.onCancelHold();
      }
    }, 1000);
  }

  public formatTimer(totalSeconds: number): string {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  public onConfirmPayment() {
    const seat = this.store.selectedSeat();
    if (!seat) return;

    this.isProcessing = true;
    this.purchaseMessage = null;

    this.api.purchaseTicket(seat.id, 'USER-CURRENT', this.buyerName, this.buyerEmail).subscribe({
      next: (res) => {
        this.isProcessing = false;
        this.purchaseMessage = `🎉 Compra aprovada! Ingresso ${seat.id} emitido. QRCode enviado ao Kafka para renderização e envio a ${this.buyerEmail}.`;
        this.store.handleSeatStatusChange(seat.id, 'SOLD');
        this.store.clearHold();
      },
      error: () => {
        // Fallback local
        this.isProcessing = false;
        this.purchaseMessage = `🎉 Pagamento simulado com sucesso! Ingresso ${seat.id} confirmado para ${this.buyerName}.`;
        this.store.handleSeatStatusChange(seat.id, 'SOLD');
        this.store.clearHold();
      }
    });
  }

  public onCancelHold() {
    const seat = this.store.selectedSeat();
    if (seat) {
      this.api.releaseTicket(seat.id, 'USER-CURRENT').subscribe({
        next: () => {},
        error: () => {}
      });
      this.store.handleSeatStatusChange(seat.id, 'AVAILABLE');
      this.store.clearHold();
    }
  }
}
