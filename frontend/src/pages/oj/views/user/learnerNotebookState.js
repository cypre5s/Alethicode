export function toggleExpandedGroup (expandedGroups, key) {
  const current = Boolean(expandedGroups && expandedGroups[key])
  return Object.assign({}, expandedGroups, { [key]: !current })
}

export function forceExpandGroup (expandedGroups, key) {
  return Object.assign({}, expandedGroups, { [key]: true })
}
