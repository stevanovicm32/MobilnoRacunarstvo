package main

import (
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/api"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
)

func main() {

	repository.InitDB()

	r := api.SetupRouter() 
	r.Run()
}
