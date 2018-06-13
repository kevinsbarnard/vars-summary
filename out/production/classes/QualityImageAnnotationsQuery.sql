SELECT DISTINCT
  ConceptName, Image
FROM
  Annotations
WHERE
  Image IS NOT NULL AND (
    ConceptName = '' OR
    ConceptName LIKE ' %'
  ) AND (
    LinkValue = 'good' OR
    LinkValue = 'close-up' OR
    LinkValue = 'selects'
  )
ORDER BY
  ConceptName ASC