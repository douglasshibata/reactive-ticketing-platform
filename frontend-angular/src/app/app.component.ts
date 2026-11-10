import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventBookingComponent } from './features/event-booking/event-booking.component';
import { CheckoutComponent } from './features/checkout/checkout.component';
import { CheckInComponent } from './features/check-in/check-in.component';
import { LiveDashboardComponent } from './shared/live-dashboard.component';
import { TicketStore } from './core/store/ticket-store.service';
import { WebSocketService } from './core/services/websocket.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    EventBookingComponent,
    CheckoutComponent,
    CheckInComponent,
    LiveDashboardComponent
  ],
  template: `
    <div class="min-h-screen bg-[#070b14] text-slate-100 flex flex-col font-sans">
      <!-- Header -->
      <header class="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur-md sticky top-0 z-50 px-6 py-4">
        <div class="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center text-xl shadow-lg shadow-brand-500/20">
              ⚡
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h1 class="text-lg font-bold text-white tracking-tight">Reactive Ticketing & Credentialing</h1>
                <span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-brand-500/20 text-brand-300 border border-brand-500/30">
                  Quarkus 3.19 + Angular 19
                </span>
              </div>
              <p class="text-xs text-slate-400">
                Plataforma Reativa de Bilhética e Credenciamento com Redis Distributed Lock & Offline-First PWA
              </p>
            </div>
          </div>

          <!-- Live WebSocket Status Badge -->
          <div class="flex items-center gap-2">
            <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border"
                  [class.bg-emerald-500/10]="wsService.isConnected()"
                  [class.text-emerald-400]="wsService.isConnected()"
                  [class.border-emerald-500/30]="wsService.isConnected()"
                  [class.bg-slate-800]="!wsService.isConnected()"
                  [class.text-slate-400]="!wsService.isConnected()"
                  [class.border-slate-700]="!wsService.isConnected()">
              <span class="w-2 h-2 rounded-full"
                    [class.bg-emerald-400]="wsService.isConnected()"
                    [class.animate-pulse]="wsService.isConnected()"
                    [class.bg-slate-500]="!wsService.isConnected()"></span>
              {{ wsService.isConnected() ? 'Mutiny WebSocket Conectado' : 'Modo Standalone / Fallback' }}
            </span>
          </div>
        </div>
      </header>

      <!-- Main Container -->
      <main class="flex-1 max-w-7xl w-full mx-auto p-6">
        <!-- Live Occupancy & Metric Cards -->
        <app-live-dashboard></app-live-dashboard>

        <!-- Navigation Tabs -->
        <div class="flex border-b border-slate-800 mb-6 gap-2">
          <button
            (click)="activeTab = 'booking'"
            class="py-2.5 px-5 text-xs font-semibold rounded-t-lg transition-all duration-150 flex items-center gap-2 border-b-2"
            [class.border-brand-500]="activeTab === 'booking'"
            [class.text-brand-400]="activeTab === 'booking'"
            [class.bg-slate-900/60]="activeTab === 'booking'"
            [class.border-transparent]="activeTab !== 'booking'"
            [class.text-slate-400]="activeTab !== 'booking'"
            [class.hover:text-slate-200]="activeTab !== 'booking'"
          >
            <span>🎫</span> Mapa de Ingressos & Venda
          </button>

          <button
            (click)="activeTab = 'checkout'"
            class="py-2.5 px-5 text-xs font-semibold rounded-t-lg transition-all duration-150 flex items-center gap-2 border-b-2 relative"
            [class.border-brand-500]="activeTab === 'checkout'"
            [class.text-brand-400]="activeTab === 'checkout'"
            [class.bg-slate-900/60]="activeTab === 'checkout'"
            [class.border-transparent]="activeTab !== 'checkout'"
            [class.text-slate-400]="activeTab !== 'checkout'"
            [class.hover:text-slate-200]="activeTab !== 'checkout'"
          >
            <span>💳</span> Checkout & Pagamento
            @if (store.isHolding()) {
              <span class="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
            }
          </button>

          <button
            (click)="activeTab = 'checkin'"
            class="py-2.5 px-5 text-xs font-semibold rounded-t-lg transition-all duration-150 flex items-center gap-2 border-b-2"
            [class.border-brand-500]="activeTab === 'checkin'"
            [class.text-brand-400]="activeTab === 'checkin'"
            [class.bg-slate-900/60]="activeTab === 'checkin'"
            [class.border-transparent]="activeTab !== 'checkin'"
            [class.text-slate-400]="activeTab !== 'checkin'"
            [class.hover:text-slate-200]="activeTab !== 'checkin'"
          >
            <span>📲</span> Credenciamento Portaria (Staff PWA)
          </button>
        </div>

        <!-- Tab Contents -->
        @if (activeTab === 'booking') {
          <app-event-booking></app-event-booking>
        } @else if (activeTab === 'checkout') {
          <app-checkout></app-checkout>
        } @else if (activeTab === 'checkin') {
          <app-check-in></app-check-in>
        }
      </main>

      <!-- Footer -->
      <footer class="border-t border-slate-900 py-4 px-6 text-center text-xs text-slate-500">
        Plataforma Reativa de Venda de Ingressos e Credenciamento &copy; 2026. Arquitetura orientada a eventos com Quarkus, Redis, Kafka e Angular.
      </footer>
    </div>
  `
})
export class AppComponent {
  public activeTab: 'booking' | 'checkout' | 'checkin' = 'booking';
  public store = inject(TicketStore);
  public wsService = inject(WebSocketService);
}
