export async function onRequest(context) {
  const url = new URL(context.request.url);
  const targetUrl = `https://lgzc-toolsmenu.lghaomaauto.workers.dev${url.pathname}${url.search}`;

  try {
    const resp = await fetch(targetUrl, {
      method: context.request.method,
      headers: context.request.headers,
      body: ['GET','HEAD'].includes(context.request.method) ? null : context.request.body,
    });

    return new Response(resp.body, {
      status: resp.status,
      statusText: resp.statusText,
      headers: resp.headers,
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: '代理请求失败', detail: e.message }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
