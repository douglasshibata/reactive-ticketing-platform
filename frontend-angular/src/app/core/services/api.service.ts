import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Seat } from '../models/ticket.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl;

  public getTickets(eventId: number = 1): Observable<Seat[]> {
    return this.http.get<Seat[]>(`${this.baseUrl}/tickets?eventId=${eventId}`);
  }

  public getEventInventory(eventId: number = 1): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/events/${eventId}/inventory`);
  }

  public reserveTicket(ticketId: string, userId: string, userEmail: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tickets/${ticketId}/reserve`, {
      userId,
      userEmail
    });
  }

  public reserveSeatLock(eventId: string, seatId: string, userId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/events/${eventId}/seats/${seatId}/lock`, {
      userId
    });
  }

  public purchaseTicket(ticketId: string, userId: string, ownerName: string, ownerEmail: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tickets/${ticketId}/purchase`, {
      userId,
      ownerName,
      ownerEmail,
      paymentMethod: 'CREDIT_CARD'
    });
  }

  public releaseTicket(ticketId: string, userId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tickets/${ticketId}/release?userId=${userId}`, {});
  }

  public scanCheckIn(ticketId: string, staffUserId: string, gate: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/check-in/scan`, {
      ticketId,
      staffUserId,
      gate
    });
  }

  public batchSyncCheckIns(scans: any[]): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/check-in/batch-sync`, {
      scans
    });
  }
}
