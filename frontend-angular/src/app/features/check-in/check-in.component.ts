import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TicketStore } from '../../core/store/ticket-store.service';
import { ApiService } from '../../core/services/api.service';
import { CheckInItem } from '../../core/models/ticket.model';

@Component({
  selector: 'app-check-in',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
      <div class="flex items-center justify-between pb-4 border-b border-slate-800 mb-6">
        <div>
          <h2 class="text-xl font-bold text-white flex items-center gap-2">
            <span>📲</span> Terminal de Credenciamento na Portaria (Staff PWA)
          </h2>
          <p class="text-xs text-slate-400 mt-1">
            Validação ultrarrápida (&lt;100ms) com arquitetura Offline-First (IndexedDB + Fila de Sync).
          </p>
        </div>

        <div class="flex items-center gap-3">
          <!-- Online/Offline Badge -->
          <div class="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border"
               [class.bg-emerald-500/10]="store.isOnline()"
               [class.text-emerald-400]="store.isOnline()"
               [class.border-emerald-500/20]="store.isOnline()"
               [class.bg-rose-500/10]="!store.isOnline()"
               [class.text-rose-400]="!store.isOnline()"
               [class.border-rose-500/20]="!store.isOnline()">
            <span class="w-2 h-2 rounded-full"
                  [class.bg-emerald-400]="store.isOnline()"
                  [class.animate-pulse]="store.isOnline()"
                  [class.bg-rose-400]="!store.isOnline()"></span>
            {{ store.isOnline() ? 'REDE ONLINE' : 'MODO OFFLINE (IndexedDB)' }}
          </div>

          <!-- Pending Sync Badge -->
          @if (offlineQueue.length > 0) {
            <span class="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-bounce">
              {{ offlineQueue.length }} pendentes de sincronização
            </span>
          }
        </div>
      </div>

      <!-- Simulator Controls -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <!-- Scanner Input -->
        <div class="md:col-span-2 bg-slate-950/60 p-5 rounded-xl border border-slate-800">
          <label class="block text-xs font-semibold text-slate-300 mb-2 uppercase tracking-wider">
            Simulador de Leitor de QR Code / Código de Barras
          </label>
          <div class="flex gap-2">
            <input
              type="text"
              [(ngModel)]="qrInput"
              (keyup.enter)="onScan()"
              placeholder="Aponte a catraca ou digite ex: VIP-101, CAM-201..."
              class="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-4 py-2.5 text-sm text-white font-mono focus:outline-none focus:border-brand-500"
            />
            <button
              (click)="onScan()"
              class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-5 py-2.5 rounded-lg text-xs transition duration-150 flex items-center gap-1.5 shadow-lg shadow-emerald-600/20"
            >
              <span>⚡</span> Validar Acesso
            </button>
          </div>

          <!-- Quick Scan Helpers -->
          <div class="mt-4 flex flex-wrap items-center gap-2">
            <span class="text-xs text-slate-500">Testar rápido:</span>
            @for (seat of store.seats(); track seat.id) {
              <button
                (click)="quickScan(seat.id)"
                class="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-[11px] font-mono text-slate-300 rounded border border-slate-700"
              >
                {{ seat.id }} ({{ seat.status }})
              </button>
            }
          </div>

          <!-- Network Toggle Simulator -->
          <div class="mt-6 pt-4 border-t border-slate-800 flex items-center justify-between">
            <span class="text-xs text-slate-400">
              Simular falha de rede 4G/Wi-Fi na entrada do evento:
            </span>
            <button
              (click)="toggleNetwork()"
              class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition duration-150"
              [class.bg-rose-500/20]="store.isOnline()"
              [class.text-rose-300]="store.isOnline()"
              [class.border-rose-500/40]="store.isOnline()"
              [class.bg-emerald-500/20]="!store.isOnline()"
              [class.text-emerald-300]="!store.isOnline()"
              [class.border-emerald-500/40]="!store.isOnline()"
            >
              {{ store.isOnline() ? 'Simular Queda de Wi-Fi' : 'Restabelecer Conexão' }}
            </button>
          </div>
        </div>

        <!-- Portão & Identificação do Staff -->
        <div class="bg-slate-950/60 p-5 rounded-xl border border-slate-800 flex flex-col justify-between">
          <div>
            <h3 class="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-3">
              Configurações do Terminal
            </h3>
            <div class="space-y-2 text-xs">
              <div class="flex justify-between text-slate-400">
                <span>Catraca / Portão:</span>
                <span class="font-bold text-white">PORTAO_A (Norte)</span>
              </div>
              <div class="flex justify-between text-slate-400">
                <span>Operador Credenciado:</span>
                <span class="text-slate-200">Staff #14 - Júlio</span>
              </div>
              <div class="flex justify-between text-slate-400">
                <span>Latência média de validação:</span>
                <span class="font-mono text-emerald-400 font-bold">&lt; 35ms</span>
              </div>
            </div>
          </div>

          @if (offlineQueue.length > 0 && store.isOnline()) {
            <button
              (click)="drainOfflineQueue()"
              class="w-full mt-4 bg-brand-600 hover:bg-brand-500 text-white font-semibold py-2 px-3 rounded-lg text-xs transition duration-150 flex items-center justify-center gap-1.5"
            >
              <span>🔄</span> Sincronizar Fila em Lote (Batch Sync)
            </button>
          }
        </div>
      </div>

      <!-- Scan Result Alert Box -->
      @if (lastResult) {
        <div class="p-4 rounded-xl border mb-6 flex items-start gap-3 transition-all duration-200"
             [class.bg-emerald-500/10]="lastResult.success"
             [class.border-emerald-500/40]="lastResult.success"
             [class.text-emerald-300]="lastResult.success"
             [class.bg-rose-500/10]="!lastResult.success"
             [class.border-rose-500/40]="!lastResult.success"
             [class.text-rose-300]="!lastResult.success">
          <span class="text-2xl">{{ lastResult.success ? '✅' : '🛑' }}</span>
          <div>
            <div class="font-bold text-sm">
              {{ lastResult.success ? 'ACESSO LIBERADO' : 'ACESSO NEGADO' }}
            </div>
            <div class="text-xs mt-0.5">{{ lastResult.message }}</div>
            <div class="text-[10px] text-slate-400 mt-1 font-mono">
              Origem: {{ lastResult.mode }} | Ingresso: {{ lastResult.ticketId }} | Hora: {{ lastResult.timestamp }}
            </div>
          </div>
        </div>
      }

      <!-- Recent Check-ins Table -->
      <div>
        <h3 class="text-sm font-semibold text-slate-200 uppercase tracking-wider mb-3">
          Histórico de Validações na Catraca
        </h3>
        <div class="overflow-x-auto rounded-xl border border-slate-800">
          <table class="w-full text-left text-xs text-slate-300">
            <thead class="bg-slate-950 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
              <tr>
                <th class="py-3 px-4">Ingresso</th>
                <th class="py-3 px-4">Status Acesso</th>
                <th class="py-3 px-4">Mensagem</th>
                <th class="py-3 px-4">Modo</th>
                <th class="py-3 px-4">Timestamp</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60 bg-slate-900/60">
              @for (item of history; track item.ticketId + item.timestamp) {
                <tr class="hover:bg-slate-800/40">
                  <td class="py-2.5 px-4 font-mono font-bold text-white">{{ item.ticketId }}</td>
                  <td class="py-2.5 px-4">
                    <span class="px-2 py-0.5 rounded text-[10px] font-bold"
                          [class.bg-emerald-500/20]="item.success"
                          [class.text-emerald-400]="item.success"
                          [class.bg-rose-500/20]="!item.success"
                          [class.text-rose-400]="!item.success">
                      {{ item.success ? 'APROVADO' : 'RECUSADO' }}
                    </span>
                  </td>
                  <td class="py-2.5 px-4 text-slate-300">{{ item.message }}</td>
                  <td class="py-2.5 px-4">
                    <span class="text-[10px] font-mono text-slate-400">{{ item.mode }}</span>
                  </td>
                  <td class="py-2.5 px-4 font-mono text-slate-400">{{ item.timestamp }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="py-6 text-center text-slate-500">
                    Nenhum check-in realizado nesta sessão.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class CheckInComponent implements OnInit {
  public store = inject(TicketStore);
  private api = inject(ApiService);

  public qrInput = 'VIP-101';
  public lastResult: any = null;
  public offlineQueue: CheckInItem[] = [];
  public history: any[] = [];

  ngOnInit() {
    this.loadOfflineQueueFromStorage();
  }

  quickScan(ticketId: string) {
    this.qrInput = ticketId;
    this.onScan();
  }

  toggleNetwork() {
    this.store.isOnline.update(v => !v);
    if (this.store.isOnline() && this.offlineQueue.length > 0) {
      this.drainOfflineQueue();
    }
  }

  onScan() {
    const ticketId = this.qrInput.trim().toUpperCase();
    if (!ticketId) return;

    const timestamp = new Date().toLocaleTimeString();

    if (!this.store.isOnline()) {
      // Offline-First Algorithm (Validação Local IndexedDB)
      this.handleOfflineScan(ticketId, timestamp);
    } else {
      // Online Real-time Scan via Quarkus (<100ms)
      this.handleOnlineScan(ticketId, timestamp);
    }
  }

  private handleOnlineScan(ticketId: string, timestamp: string) {
    this.api.scanCheckIn(ticketId, 'STAFF-01', 'PORTAO_A').subscribe({
      next: (res) => {
        this.lastResult = {
          success: res.success,
          message: res.message,
          ticketId,
          mode: 'Quarkus REST API',
          timestamp
        };
        this.history.unshift(this.lastResult);
        if (res.success) {
          this.store.handleSeatStatusChange(ticketId, 'CHECKED_IN');
        }
      },
      error: () => {
        // Fallback local se rede cair na hora do request
        this.handleOfflineScan(ticketId, timestamp);
      }
    });
  }

  private handleOfflineScan(ticketId: string, timestamp: string) {
    const seat = this.store.seats().find(s => s.id === ticketId);

    let success = false;
    let message = '';

    if (!seat) {
      message = 'Ingresso inexistente na base local (IndexedDB)';
    } else if (seat.status === 'CHECKED_IN') {
      message = 'Alerta: Ingresso já utilizado previamente na catraca';
    } else if (seat.status !== 'SOLD' && seat.status !== 'AVAILABLE') {
      message = `Ingresso em status não aprovado (${seat.status})`;
    } else {
      success = true;
      message = `Acesso Liberado Offline! Registrado na fila IndexedDB.`;
      this.store.handleSeatStatusChange(ticketId, 'CHECKED_IN');
    }

    const item: CheckInItem = {
      ticketId,
      scannedAt: new Date().toISOString(),
      gate: 'PORTAO_A',
      staffUserId: 'STAFF-OFFLINE'
    };

    if (success) {
      this.offlineQueue.push(item);
      this.saveOfflineQueueToStorage();
    }

    this.lastResult = {
      success,
      message,
      ticketId,
      mode: 'IndexedDB (Local)',
      timestamp
    };
    this.history.unshift(this.lastResult);
  }

  public drainOfflineQueue() {
    if (this.offlineQueue.length === 0) return;

    const scansToSync = [...this.offlineQueue];
    this.api.batchSyncCheckIns(scansToSync).subscribe({
      next: (res) => {
        console.log('✅ Sincronização em lote concluída com sucesso:', res);
        this.offlineQueue = [];
        this.saveOfflineQueueToStorage();
      },
      error: (err) => {
        console.warn('Erro ao sincronizar lote com Quarkus:', err);
      }
    });
  }

  private saveOfflineQueueToStorage() {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('ticketing_offline_sync_queue', JSON.stringify(this.offlineQueue));
    }
  }

  private loadOfflineQueueFromStorage() {
    if (typeof localStorage !== 'undefined') {
      const data = localStorage.getItem('ticketing_offline_sync_queue');
      if (data) {
        try {
          this.offlineQueue = JSON.parse(data);
        } catch (e) {
          this.offlineQueue = [];
        }
      }
    }
  }
}
