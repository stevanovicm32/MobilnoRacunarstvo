package main

import (
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/api"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
)

func main() {

	repository.InitDB()

	userRepo := repository.NewUserRepository()
	dropRepo := repository.NewDropRepository()

	authHandler := api.NewAuthHandler(userRepo)
	dropHandler := api.NewDropHandler(dropRepo, userRepo)

	r := api.SetupRouter(authHandler, dropHandler)
	r.Run()
}
