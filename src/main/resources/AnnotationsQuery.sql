SELECT DISTINCT
  ConceptName, Image
FROM
  Annotations
WHERE
  Image IS NOT NULL AND (
    ConceptName = '' OR
    ConceptName LIKE ' %'
  )
ORDER BY
  ConceptName ASC