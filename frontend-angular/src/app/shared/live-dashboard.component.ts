import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TicketStore } from '../../core/store/ticket-store.service';

@Component({
  selector: 'app-live-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      <!-- Total Lotação -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
        <div>
          <div class="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">Lotação Total</div>
          <div class="text-2xl font-bold text-white mt-1">{{ store.totalCapacity() }}</div>
          <div class="text-[10px] text-slate-500 mt-0.5">Capacidade Máxima</div>
        </div>
        <div class="w-10 h-10 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center text-xl font-bold">
          🏟️
        </div>
      </div>

      <!-- Ocupação / Vendas -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
        <div>
          <div class="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">Ingressos Vendidos</div>
          <div class="text-2xl font-bold text-emerald-400 mt-1">
            {{ store.soldCount() }}
            <span class="text-xs text-slate-400 font-normal">({{ store.occupancyPercentage() }}%)</span>
          </div>
          <div class="text-[10px] text-slate-500 mt-0.5">{{ store.availableCount() }} disponíveis</div>
        </div>
        <div class="w-10 h-10 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center text-xl font-bold">
          💰
        </div>
      </div>

      <!-- Reservas Ativas (Redis Locks) -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
        <div>
          <div class="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">Locks Ativos (Redis)</div>
          <div class="text-2xl font-bold text-amber-400 mt-1">{{ store.lockedCount() }}</div>
          <div class="text-[10px] text-slate-500 mt-0.5">TTL de 10 minutos (SETNX)</div>
        </div>
        <div class="w-10 h-10 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center text-xl font-bold">
          🔒
        </div>
      </div>

      <!-- Presença Física no Local (Catracas) -->
      <div class="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
        <div>
          <div class="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">Check-in Realizado</div>
          <div class="text-2xl font-bold text-purple-400 mt-1">
            {{ store.checkedInCount() }}
            <span class="text-xs text-slate-400 font-normal">({{ store.checkInPercentage() }}%)</span>
          </div>
          <div class="text-[10px] text-slate-500 mt-0.5">Presentes no local físico</div>
        </div>
        <div class="w-10 h-10 rounded-lg bg-purple-500/10 text-purple-400 flex items-center justify-center text-xl font-bold">
          🎟️
        </div>
      </div>
    </div>
  `
})
export class LiveDashboardComponent {
  public store = inject(TicketStore);
}
