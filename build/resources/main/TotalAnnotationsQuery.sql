SELECT
  ConceptName
FROM
  Annotations
WHERE
  ConceptName = '' OR
  ConceptName LIKE ' %'
ORDER BY
  ConceptName ASC