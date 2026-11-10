// src/app/features/event-booking/services/vod.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface VodAccessResponse {
  secureUrl: string; // URL Pré-assinada com hash criptográfico da AWS
  expiresAt: string; // Timestamp de quando o link "morre"
}

@Injectable({ providedIn: 'root' })
export class VodService {
  private http = inject(HttpClient);

  public getSecureStreamUrl(eventId: string): Observable<VodAccessResponse> {
    // O Interceptor do Angular já deve estar a injetar o Bearer JWT do utilizador aqui
    return this.http.get<VodAccessResponse>(`/api/events/${eventId}/vod-access`);
  }
}
