import { Injectable, signal } from '@angular/core';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { Observable, Subject } from 'rxjs';
import { retry, delay, tap, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { WebSocketMessage } from '../models/ticket.model';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private socket$!: WebSocketSubject<any>;
  private messagesSubject = new Subject<WebSocketMessage>();
  public isConnected = signal<boolean>(false);

  constructor() {
    this.connect();
  }

  public connect(): void {
    if (this.socket$ && !this.socket$.closed) {
      return;
    }

    try {
      this.socket$ = webSocket({
        url: environment.wsUrl,
        openObserver: {
          next: () => {
            console.log('✅ WebSocket conectado com sucesso ao Quarkus Live Stream.');
            this.isConnected.set(true);
          }
        },
        closeObserver: {
          next: () => {
            console.warn('⚠️ Conexão WebSocket encerrada. Aguardando reconexão RxJS...');
            this.isConnected.set(false);
          }
        }
      });

      this.socket$.pipe(
        // Resiliência RxJS: auto-reconexão com backoff de 3s
        retry({ delay: 3000 }),
        tap(msg => console.log('⚡ Evento ao vivo recebido do servidor:', msg))
      ).subscribe({
        next: (msg: WebSocketMessage) => this.messagesSubject.next(msg),
        error: (err) => console.error('Erro na subscrição WebSocket:', err)
      });
    } catch (e) {
      console.warn('WebSocket inicialização em modo offline/desconectado:', e);
      this.isConnected.set(false);
    }
  }

  public getMessages(): Observable<WebSocketMessage> {
    return this.messagesSubject.asObservable();
  }

  public sendMessage(msg: any): void {
    if (this.socket$ && !this.socket$.closed) {
      this.socket$.next(msg);
    }
  }

  public close(): void {
    if (this.socket$) {
      this.socket$.complete();
      this.isConnected.set(false);
    }
  }
}
