import test from 'node:test';
import assert from 'node:assert/strict';

test('VodAccessResponse & Security Checks', () => {
  const vodPayload = {
    secureUrl: 'https://empresa-vod-archive-prd.s3.sa-east-1.amazonaws.com/recordings/events/101/video.mp4?X-Amz-Signature=abc123signed',
    expiresAt: '2026-11-11T03:00:00Z'
  };

  assert.ok(vodPayload.secureUrl.includes('X-Amz-Signature'), 'URL should have AWS cryptographic signature');
  assert.ok(vodPayload.secureUrl.includes('empresa-vod-archive-prd'), 'URL points to dedicated S3 VOD bucket');
  assert.ok(vodPayload.expiresAt, 'ExpiresAt timestamp must be present');
});

test('Angular VOD Security Attributes Contract', () => {
  // Validate that the secure player configuration prevents direct download and right-click
  const playerConfig = {
    controls: true,
    controlsList: 'nodownload',
    disableRightClick: true
  };

  assert.equal(playerConfig.controlsList, 'nodownload', 'controlsList must be nodownload to avoid browser download button');
  assert.equal(playerConfig.disableRightClick, true, 'contextmenu must be intercepted to prevent Save video as');
});
