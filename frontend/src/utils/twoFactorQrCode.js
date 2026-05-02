import QRCode from 'qrcode'

const OT_AUTH_SCHEME = 'otpauth://'

export async function resolveTwoFactorQrSrc(rawValue) {
  const value = typeof rawValue === 'string' ? rawValue.trim() : ''
  if (!value) {
    return ''
  }
  if (!value.toLowerCase().startsWith(OT_AUTH_SCHEME)) {
    return value
  }
  return QRCode.toDataURL(value, {
    errorCorrectionLevel: 'M',
    margin: 1,
    width: 300
  })
}
