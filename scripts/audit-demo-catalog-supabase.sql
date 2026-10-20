-- Read-only audit for demonstration catalog changes in production.
-- Historical demo-looking events can still have issued tickets and completed
-- purchases linked through tickets.purchase_id even when purchases.event_id
-- is null. Do not shift their dates or ownership without reviewing them.
SELECT e.id,
       e.title,
       e.status,
       e.organizer_email,
       e.event_date,
       (SELECT count(*) FROM connectify.tickets t WHERE t.event_id = e.id) AS issued_tickets,
       (SELECT count(DISTINCT p.id)
          FROM connectify.purchases p
          LEFT JOIN connectify.tickets t ON t.purchase_id = p.id
         WHERE p.event_id = e.id OR t.event_id = e.id) AS associated_purchases
  FROM connectify.events e
 WHERE e.id BETWEEN 1 AND 25
 ORDER BY e.id;
