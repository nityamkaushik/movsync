const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

export function generateRoomCode(length = 6) {
  let code = '';
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  for (let i = 0; i < length; i++) {
    code += ALPHABET[array[i] % ALPHABET.length];
  }
  return code;
}
