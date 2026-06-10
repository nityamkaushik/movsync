import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { AccessToken } from "https://esm.sh/livekit-server-sdk@1.2.6"

serve(async (req) => {
  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  }

  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { room, participantName, participantIdentity } = await req.json()

    if (!room || !participantIdentity) {
      return new Response(
        JSON.stringify({ error: "Missing room or participantIdentity" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      )
    }

    const apiKey = Deno.env.get('LIVEKIT_API_KEY')
    const apiSecret = Deno.env.get('LIVEKIT_API_SECRET')

    if (!apiKey || !apiSecret) {
      return new Response(
        JSON.stringify({ error: "Missing LiveKit credentials" }), 
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      )
    }

    const at = new AccessToken(apiKey, apiSecret, {
      identity: participantIdentity,
      name: participantName,
    })

    at.addGrant({ roomJoin: true, room: room })

    return new Response(
      JSON.stringify({ token: await at.toJwt() }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
