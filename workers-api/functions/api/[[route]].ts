export async function onRequest(context) {
  const url = new URL(context.request.url);
  const targetUrl = `https://lgzc-toolsmenu.lghaomaauto.workers.dev${url.pathname}${url.search}`;

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
}
