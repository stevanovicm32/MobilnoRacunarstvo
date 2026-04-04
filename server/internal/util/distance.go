package util

import "math"

// Haversine distance between two points in meters
func Distance(lat1, lon1, lat2, lon2 float64) float64 {
	const R = 6371000 // Radius of the Earth, I guess? 
	rad := math.Pi / 180
	
	phi1, phi2 := lat1*rad, lat2*rad
	dPhi := (lat2 - lat1) * rad
	dLambda := (lon2 - lon1) * rad

	a := math.Sin(dPhi/2)*math.Sin(dPhi/2) +
		math.Cos(phi1)*math.Cos(phi2)*
			math.Sin(dLambda/2)*math.Sin(dLambda/2)
	
	return R * 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
}
