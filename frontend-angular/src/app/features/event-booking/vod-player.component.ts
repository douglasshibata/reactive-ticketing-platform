// src/app/features/event-booking/vod-player.component.ts
import { Component, OnInit, inject, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { VodService } from './services/vod.service';

@Component({
  selector: 'app-vod-player',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="video-container">
      <h2>Gravação Exclusiva do Evento</h2>

      @if (isLoading()) {
        <div class="loader">A validar credenciais e a carregar vídeo...</div>
      } @else if (errorMsg()) {
        <div class="error-box">
          <p>Acesso negado: {{ errorMsg() }}</p>
          <button (click)="buyTicket()">Comprar Acesso VOD</button>
        </div>
      } @else {
        <!--
          controlsList="nodownload": Remove o botão nativo de download no Chrome/Edge
          oncontextmenu="return false;": Bloqueia o clique direito "Guardar vídeo como..."
        -->
        <video
          class="secure-player"
          controls
          controlsList="nodownload"
          (contextmenu)="preventRightClick($event)"
          [src]="videoUrl()">
          O seu navegador não suporta a tag de vídeo.
        </video>
      }
    </div>
  `,
  styles: [`
    .video-container { max-width: 800px; margin: 0 auto; background: #000; border-radius: 12px; }
    .secure-player { width: 100%; border-radius: 12px; outline: none; }
    .error-box { padding: 40px; text-align: center; color: #ff5252; background: #1a1a1a; }
    .loader { padding: 40px; text-align: center; color: #a0aec0; }
  `]
})
export class VodPlayerComponent implements OnInit {
  private vodService = inject(VodService);
  private sanitizer = inject(DomSanitizer);

  public videoUrl = signal<SafeUrl | null>(null);
  public isLoading = signal<boolean>(true);
  public errorMsg = signal<string | null>(null);

  @Input() public eventId = '101'; // Passado via @Input() ou Router Param em produção

  ngOnInit() {
    this.vodService.getSecureStreamUrl(this.eventId).subscribe({
      next: (response) => {
        // O Angular exige que URLs de media dinâmicas sejam sanitizadas
        const safeUrl = this.sanitizer.bypassSecurityTrustUrl(response.secureUrl);
        this.videoUrl.set(safeUrl);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMsg.set('Não possui um bilhete válido para assistir a esta gravação.');
        this.isLoading.set(false);
      }
    });
  }

  // Defesa extra: Impede que o utilizador clique com o botão direito para guardar
  preventRightClick(event: MouseEvent) {
    event.preventDefault();
  }

  buyTicket() {
    // Redirecionamento ou emissão de evento para compra
  }
}
