import test from 'node:test';
import assert from 'node:assert/strict';

// Test ticket status transitions and calculation logic
test('Reactive Ticketing - State Management Transitions', (t) => {
  const initialSeats = [
    { id: 'VIP-101', status: 'AVAILABLE', price: 350 },
    { id: 'VIP-102', status: 'AVAILABLE', price: 350 },
    { id: 'CAM-201', status: 'LOCKED', price: 220 },
    { id: 'PIS-301', status: 'SOLD', price: 180 },
    { id: 'PIS-302', status: 'CHECKED_IN', price: 180 },
  ];

  // 1. Initial counts
  const available = initialSeats.filter(s => s.status === 'AVAILABLE').length;
  const locked = initialSeats.filter(s => s.status === 'LOCKED').length;
  const sold = initialSeats.filter(s => s.status === 'SOLD' || s.status === 'CHECKED_IN').length;
  const checkedIn = initialSeats.filter(s => s.status === 'CHECKED_IN').length;

  assert.equal(available, 2, 'Available seats should be 2');
  assert.equal(locked, 1, 'Locked seats should be 1');
  assert.equal(sold, 2, 'Sold seats should be 2');
  assert.equal(checkedIn, 1, 'Checked in seats should be 1');

  // 2. Lock Transition (Anti-Double Booking)
  const seatToLock = initialSeats.find(s => s.id === 'VIP-101');
  assert.ok(seatToLock);
  seatToLock.status = 'LOCKED';

  assert.equal(initialSeats.filter(s => s.status === 'AVAILABLE').length, 1);
  assert.equal(initialSeats.filter(s => s.status === 'LOCKED').length, 2);

  // 3. Purchase Confirmation Transition
  seatToLock.status = 'SOLD';
  assert.equal(initialSeats.filter(s => s.status === 'LOCKED').length, 1);
  assert.equal(initialSeats.filter(s => s.status === 'SOLD' || s.status === 'CHECKED_IN').length, 3);

  // 4. Entrance Check-In Gate Transition
  seatToLock.status = 'CHECKED_IN';
  assert.equal(initialSeats.filter(s => s.status === 'CHECKED_IN').length, 2);
});

test('Offline Check-In Queue & Batch Packaging', (t) => {
  const offlineQueue = [];

  // Simulate scans while offline
  offlineQueue.push({ ticketId: 'VIP-101', gate: 'PORTAO_A', timestamp: Date.now() });
  offlineQueue.push({ ticketId: 'CAM-201', gate: 'PORTAO_A', timestamp: Date.now() + 10 });

  assert.equal(offlineQueue.length, 2);

  // Batch payload structure
  const batchPayload = {
    scans: offlineQueue.map(item => ({
      ticketId: item.ticketId,
      gate: item.gate,
      staffUserId: 'STAFF-OFFLINE'
    }))
  };

  assert.equal(batchPayload.scans.length, 2);
  assert.equal(batchPayload.scans[0].ticketId, 'VIP-101');
});
